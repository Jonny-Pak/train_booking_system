package com.example.ticketbooking.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.ticketbooking.booking.BookingExpiryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.LinkedMultiValueMap;

@SpringBootTest(properties = {"vnpay.enabled=true", "vnpay.tmn-code=P6TEST01",
        "vnpay.hash-secret=test-only-vnpay-secret-32-characters"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PaymentIntegrationTest.FixedTime.class)
class PaymentIntegrationTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final String IPN = "/api/v1/payments/vnpay/ipn";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired VnpayGateway gateway;
    @Autowired BookingExpiryService expiry;
    private long customer, other, route, vehicle, trip, firstSeat, secondSeat;

    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock paymentClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    @BeforeEach
    void setup() {
        customer = account(); other = account();
        route = jdbc.queryForObject("INSERT INTO routes(origin,destination) VALUES ('P6 A','P6 B') RETURNING id", Long.class);
        vehicle = jdbc.queryForObject("INSERT INTO vehicles(vehicle_code,vehicle_type,display_name) VALUES (?,'BUS','P6 Bus') RETURNING id", Long.class, "P6-" + UUID.randomUUID());
        trip = jdbc.queryForObject("INSERT INTO trips(route_id,vehicle_id,departure_at,arrival_at,base_price) VALUES (?,?,?,?,150000) RETURNING id",
                Long.class, route, vehicle, java.sql.Timestamp.from(NOW.plusSeconds(7200)), java.sql.Timestamp.from(NOW.plusSeconds(14400)));
        firstSeat = seat("A1"); secondSeat = seat("A2");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM tickets WHERE booking_seat_id IN (SELECT id FROM booking_seats WHERE trip_id=?)", trip);
        jdbc.update("DELETE FROM payments WHERE booking_id IN (SELECT id FROM bookings WHERE trip_id=?)", trip);
        jdbc.update("DELETE FROM booking_seats WHERE trip_id=?", trip);
        jdbc.update("DELETE FROM bookings WHERE trip_id=?", trip);
        jdbc.update("DELETE FROM trips WHERE id=?", trip);
        jdbc.update("DELETE FROM seats WHERE vehicle_id=?", vehicle);
        jdbc.update("DELETE FROM vehicles WHERE id=?", vehicle);
        jdbc.update("DELETE FROM routes WHERE id=?", route);
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", customer, other);
    }

    @Test
    void createsSignedSandboxUrlWithDbAmountAndReusesPendingAttempt() throws Exception {
        long booking = book(customer, firstSeat, secondSeat);
        JsonNode first = pay(booking);
        JsonNode second = pay(booking);
        assertThat(second.get("txnRef")).isEqualTo(first.get("txnRef"));
        assertThat(first.get("amount").decimalValue()).isEqualByComparingTo("300000");
        assertThat(first.get("paymentUrl").asText()).startsWith(VnpayGateway.SANDBOX_URL + "?")
                .contains("vnp_Amount=30000000", "vnp_ExpireDate=20300101071000", "vnp_CreateDate=20300101070000");
        assertThat(count("payments")).isEqualTo(1);
        assertThat(bookingStatus(booking)).isEqualTo("PENDING");
        assertThat(count("tickets")).isZero();
    }

    @Test
    void validIpnPaysBookingIssuesOneTicketPerSeatAndRepeatedIpnDoesNothing() throws Exception {
        long booking = book(customer, firstSeat, secondSeat);
        JsonNode payment = pay(booking);
        var fields = callback(payment, "00", "00", "123456");
        ipn(fields).andExpect(jsonPath("$.RspCode").value("00"));
        ipn(fields).andExpect(jsonPath("$.RspCode").value("02"));
        assertThat(bookingStatus(booking)).isEqualTo("PAID");
        assertThat(count("tickets")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_seats WHERE trip_id=? AND is_active", Integer.class, trip)).isEqualTo(2);
        mvc.perform(get("/api/v1/bookings/" + booking + "/tickets").with(user(""+customer).roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].seatCode").value("A1")).andExpect(jsonPath("$[0].passengerName").value("P6 Customer"))
                .andExpect(jsonPath("$[0].qrContent").value(org.hamcrest.Matchers.startsWith("LEN_DUONG:TKT")));
        mvc.perform(post("/api/v1/bookings/" + booking + "/cancel").with(user(""+customer).roles("CUSTOMER")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/bookings/" + booking + "/payments").with(user(""+customer).roles("CUSTOMER")))
                .andExpect(status().isConflict());
    }

    @Test
    void tamperedSignatureMerchantDuplicateFieldsAndAmountDoNotChangeState() throws Exception {
        long booking = book(customer, firstSeat);
        JsonNode payment = pay(booking);
        var fields = callback(payment,"00","00","123456");
        var signed = signed(fields); signed.set("vnp_Amount","1");
        mvc.perform(get(IPN).params(signed)).andExpect(jsonPath("$.RspCode").value("97"));
        signed = signed(fields); signed.add("vnp_Amount", fields.get("vnp_Amount"));
        mvc.perform(get(IPN).params(signed)).andExpect(jsonPath("$.RspCode").value("97"));
        fields.put("vnp_TmnCode","OTHER001");
        ipn(fields).andExpect(jsonPath("$.RspCode").value("97"));
        fields.put("vnp_TmnCode","P6TEST01"); fields.put("vnp_Amount","1");
        ipn(fields).andExpect(jsonPath("$.RspCode").value("04"));
        fields.put("vnp_Amount", "15000000"); fields.put("vnp_TxnRef", "unknown");
        ipn(fields).andExpect(jsonPath("$.RspCode").value("01"));
        assertThat(bookingStatus(booking)).isEqualTo("PENDING"); assertThat(count("tickets")).isZero();
    }

    @Test
    void browserReturnCannotConfirmAndOtherCustomersCannotReadPaymentsOrTickets() throws Exception {
        long booking = book(customer, firstSeat);
        JsonNode payment = pay(booking);
        mvc.perform(get("/api/v1/payments/" + payment.get("txnRef").asText()).with(user(""+customer).roles("CUSTOMER"))
                .param("vnp_ResponseCode","00").param("vnp_TransactionStatus","00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        for (String path : List.of("/api/v1/bookings/"+booking+"/tickets", "/api/v1/bookings/"+booking+"/payments", "/api/v1/payments/"+payment.get("txnRef").asText())) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(user(""+other).roles("CUSTOMER"))).andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/v1/bookings/"+booking+"/payments").with(user(""+other).roles("CUSTOMER"))).andExpect(status().isNotFound());
        assertThat(bookingStatus(booking)).isEqualTo("PENDING"); assertThat(count("tickets")).isZero();
    }

    @Test
    void failedPaymentAllowsANewAttemptButDuplicateFailureIsIdempotent() throws Exception {
        long booking = book(customer, firstSeat);
        JsonNode payment = pay(booking);
        var failed = callback(payment,"24","02","0");
        ipn(failed).andExpect(jsonPath("$.RspCode").value("00"));
        ipn(failed).andExpect(jsonPath("$.RspCode").value("02"));
        JsonNode retry = pay(booking);
        assertThat(retry.get("txnRef")).isNotEqualTo(payment.get("txnRef"));
        ipn(callback(retry,"00","00","888888")).andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(bookingStatus(booking)).isEqualTo("PAID"); assertThat(count("tickets")).isEqualTo(1);
        ipn(callback(retry,"24","02","0")).andExpect(jsonPath("$.RspCode").value("99"));
        assertThat(bookingStatus(booking)).isEqualTo("PAID");
    }

    @Test
    void lateSuccessForCancelledBookingIsRecordedForReconciliationWithoutTakingSeatsBack() throws Exception {
        long booking = book(customer, firstSeat); JsonNode payment = pay(booking);
        mvc.perform(post("/api/v1/bookings/"+booking+"/cancel").with(user(""+customer).roles("CUSTOMER"))).andExpect(status().isOk());
        long replacement = book(other, firstSeat);
        var fields = callback(payment,"00","00","555555");
        ipn(fields).andExpect(jsonPath("$.RspCode").value("00"));
        ipn(fields).andExpect(jsonPath("$.RspCode").value("02"));
        assertThat(bookingStatus(booking)).isEqualTo("CANCELLED"); assertThat(bookingStatus(replacement)).isEqualTo("PENDING");
        assertThat(count("tickets")).isZero();
        assertThat(jdbc.queryForObject("SELECT reconciliation_reason FROM payments WHERE booking_id=?",String.class,booking)).isEqualTo("BOOKING_CANCELLED");
        mvc.perform(get("/api/v1/admin/payments/reconciliation").with(user(""+customer).roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/payments/reconciliation").with(user(""+other).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].reconciliationRequired").value(true));
    }

    @Test
    void deadlineBoundaryExpiresBookingAndLateIpnNeverIssuesTickets() throws Exception {
        long booking = book(customer, firstSeat); JsonNode payment = pay(booking);
        jdbc.update("UPDATE bookings SET hold_expires_at=? WHERE id=?", java.sql.Timestamp.from(NOW), booking);
        ipn(callback(payment,"00","00","666666")).andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(bookingStatus(booking)).isEqualTo("EXPIRED"); assertThat(count("tickets")).isZero();
        assertThat(jdbc.queryForObject("SELECT is_active FROM booking_seats WHERE booking_id=?",Boolean.class,booking)).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE booking_id=?",String.class,booking)).isEqualTo("SUCCESS");
    }

    @Test
    void duplicateProviderTransactionCannotPayTwoBookings() throws Exception {
        long first = book(customer, firstSeat); long second = book(customer, secondSeat);
        JsonNode one = pay(first), two = pay(second);
        ipn(callback(one,"00","00","777777")).andExpect(jsonPath("$.RspCode").value("00"));
        ipn(callback(two,"00","00","777777")).andExpect(jsonPath("$.RspCode").value("99"));
        assertThat(bookingStatus(second)).isEqualTo("PENDING"); assertThat(count("tickets")).isEqualTo(1);
    }

    @Test
    void conflictingSuccessAfterFailureNeedsReconciliationAndBlocksRetry() throws Exception {
        long booking = book(customer, firstSeat); JsonNode payment = pay(booking);
        ipn(callback(payment,"24","02","0")).andExpect(jsonPath("$.RspCode").value("00"));
        ipn(callback(payment,"00","00","333333")).andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(bookingStatus(booking)).isEqualTo("PENDING"); assertThat(count("tickets")).isZero();
        mvc.perform(post("/api/v1/bookings/"+booking+"/payments").with(user(""+customer).roles("CUSTOMER")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_RECONCILIATION_REQUIRED"));
    }

    @Test
    void invalidProviderDateAndFractionalAmountsAreRejected() throws Exception {
        long booking = book(customer, firstSeat); JsonNode payment = pay(booking);
        var fields = callback(payment,"00","00","123456"); fields.put("vnp_PayDate","20300230070000");
        ipn(fields).andExpect(jsonPath("$.RspCode").value("99"));
        jdbc.update("UPDATE bookings SET total_amount=100.50 WHERE id=?",booking);
        jdbc.update("DELETE FROM payments WHERE booking_id=?",booking);
        mvc.perform(post("/api/v1/bookings/"+booking+"/payments").with(user(""+customer).roles("CUSTOMER")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAYMENT_AMOUNT"));
        assertThat(count("tickets")).isZero();
    }

    @Test
    void concurrentDuplicateIpnIssuesTicketsExactlyOnce() throws Exception {
        long booking = book(customer, firstSeat, secondSeat); JsonNode payment = pay(booking);
        var fields = callback(payment,"00","00","999999");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> task = () -> { start.await(); return ipn(fields).andReturn().getResponse().getContentAsString(); };
            var one = executor.submit(task); var two = executor.submit(task); start.countDown();
            List<String> codes = List.of(mapper.readTree(one.get(15,TimeUnit.SECONDS)).get("RspCode").asText(), mapper.readTree(two.get(15,TimeUnit.SECONDS)).get("RspCode").asText());
            assertThat(codes).containsExactlyInAnyOrder("00","02");
        }
        assertThat(count("tickets")).isEqualTo(2); assertThat(bookingStatus(booking)).isEqualTo("PAID");
    }

    @Test
    void ticketInsertFailureRollsBackPaymentBookingAndAllTickets() throws Exception {
        long booking = book(customer, firstSeat, secondSeat); JsonNode payment = pay(booking);
        String function = "p6_fail_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE FUNCTION " + function + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Test ticket write failure'; END $$");
        jdbc.execute("CREATE TRIGGER " + function + " BEFORE INSERT ON tickets FOR EACH ROW EXECUTE FUNCTION " + function + "()");
        try {
            ipn(callback(payment,"00","00","121212")).andExpect(jsonPath("$.RspCode").value("99"));
            assertThat(bookingStatus(booking)).isEqualTo("PENDING"); assertThat(count("tickets")).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE booking_id=?",String.class,booking)).isEqualTo("PENDING");
        } finally {
            jdbc.execute("DROP TRIGGER " + function + " ON tickets"); jdbc.execute("DROP FUNCTION " + function + "()");
        }
        ipn(callback(payment,"00","00","121212")).andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(count("tickets")).isEqualTo(2);
    }

    private long account() {
        return jdbc.queryForObject("INSERT INTO users(full_name,email,password_hash,email_verified) VALUES ('P6 Customer',?,'fixture',true) RETURNING id", Long.class, UUID.randomUUID()+"@example.invalid");
    }

    @Test
    void concurrentCreateReusesOnePendingAttempt() throws Exception {
        long booking = book(customer, firstSeat);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> call = () -> { start.await(); return pay(booking).get("txnRef").asText(); };
            var first = executor.submit(call); var second = executor.submit(call); start.countDown();
            assertThat(first.get(15,TimeUnit.SECONDS)).isEqualTo(second.get(15,TimeUnit.SECONDS));
        }
        assertThat(count("payments")).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"01", "04", "05", "06", "07", "09"})
    void providerReviewStatusesNeverIssueTicketsOrAllowAnotherCharge(String status) throws Exception {
        long booking = book(customer, firstSeat); JsonNode payment = pay(booking);
        ipn(callback(payment,status.equals("07") ? "07" : "99",status,"454545")).andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(count("tickets")).isZero(); assertThat(bookingStatus(booking)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT reconciliation_reason FROM payments WHERE booking_id=?",String.class,booking)).isEqualTo("PROVIDER_REVIEW_REQUIRED");
        mvc.perform(post("/api/v1/bookings/"+booking+"/payments").with(user(""+customer).roles("CUSTOMER")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_RECONCILIATION_REQUIRED"));
    }

    @Test
    void cancellationAndIpnRaceNeverLeavesPaidSeatsReleased() throws Exception {
        long booking = book(customer, firstSeat); JsonNode payment = pay(booking);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var paid = executor.submit(() -> { start.await(); return ipn(callback(payment,"00","00","414141")).andReturn(); });
            var cancelled = executor.submit(() -> { start.await(); return mvc.perform(post("/api/v1/bookings/"+booking+"/cancel")
                    .with(user(""+customer).roles("CUSTOMER"))).andReturn(); });
            start.countDown();
            assertThat(paid.get(15,TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
            int cancelStatus = cancelled.get(15,TimeUnit.SECONDS).getResponse().getStatus();
            if (bookingStatus(booking).equals("PAID")) {
                assertThat(cancelStatus).isEqualTo(409); assertThat(count("tickets")).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT is_active FROM booking_seats WHERE booking_id=?",Boolean.class,booking)).isTrue();
            } else {
                assertThat(bookingStatus(booking)).isEqualTo("CANCELLED"); assertThat(cancelStatus).isEqualTo(200);
                assertThat(count("tickets")).isZero();
                assertThat(jdbc.queryForObject("SELECT reconciliation_required FROM payments WHERE booking_id=?",Boolean.class,booking)).isTrue();
            }
        }
    }

    @Test
    void expirySchedulerAndIpnRaceAtTheDeadlineNeverCreatesTickets() throws Exception {
        long booking = book(customer, firstSeat); JsonNode payment = pay(booking);
        jdbc.update("UPDATE bookings SET hold_expires_at=? WHERE id=?",java.sql.Timestamp.from(NOW),booking);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var expired = executor.submit(() -> { start.await(); expiry.expireTrip(trip); return true; });
            var paid = executor.submit(() -> { start.await(); return ipn(callback(payment,"00","00","424242")).andReturn(); });
            start.countDown(); expired.get(15,TimeUnit.SECONDS); paid.get(15,TimeUnit.SECONDS);
        }
        assertThat(bookingStatus(booking)).isEqualTo("EXPIRED"); assertThat(count("tickets")).isZero();
        assertThat(jdbc.queryForObject("SELECT is_active FROM booking_seats WHERE booking_id=?",Boolean.class,booking)).isFalse();
    }
    private long seat(String code) { return jdbc.queryForObject("INSERT INTO seats(vehicle_id,seat_code) VALUES (?,?) RETURNING id",Long.class,vehicle,code); }
    private long book(long owner, Long... selected) throws Exception {
        var result = mvc.perform(post("/api/v1/bookings").with(user(""+owner).roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("tripId",trip,"seatIds",selected))))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
    private JsonNode pay(long booking) throws Exception {
        var result = mvc.perform(post("/api/v1/bookings/"+booking+"/payments").with(user(""+customer).roles("CUSTOMER")))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }
    private Map<String,String> callback(JsonNode payment,String response,String status,String providerId) {
        Map<String,String> fields = new TreeMap<>();
        fields.put("vnp_TxnRef",payment.get("txnRef").asText()); fields.put("vnp_TmnCode","P6TEST01");
        fields.put("vnp_Amount",VnpayGateway.amount(payment.get("amount").decimalValue()));
        fields.put("vnp_ResponseCode",response); fields.put("vnp_TransactionStatus",status);
        fields.put("vnp_TransactionNo",providerId); fields.put("vnp_PayDate",VnpayGateway.format(NOW));
        return fields;
    }
    private LinkedMultiValueMap<String,String> signed(Map<String,String> fields) {
        var query = new LinkedMultiValueMap<String,String>(); fields.forEach(query::set);
        query.set("vnp_SecureHash",gateway.sign(fields)); return query;
    }
    private ResultActions ipn(Map<String,String> fields) throws Exception { return mvc.perform(get(IPN).params(signed(fields))).andExpect(status().isOk()); }
    private String bookingStatus(long booking) { return jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,booking); }
    private int count(String table) {
        if (table.equals("tickets")) return jdbc.queryForObject("SELECT count(*) FROM tickets t JOIN booking_seats bs ON bs.id=t.booking_seat_id WHERE bs.trip_id=?",Integer.class,trip);
        return jdbc.queryForObject("SELECT count(*) FROM payments p JOIN bookings b ON b.id=p.booking_id WHERE b.trip_id=?",Integer.class,trip);
    }
}
