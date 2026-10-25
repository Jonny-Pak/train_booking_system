package com.example.ticketbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BookingIntegrationTest.FixedTime.class)
class BookingIntegrationTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final String API = "/api/v1/bookings";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired BookingExpiryService expiry;

    private long customer;
    private long otherCustomer;
    private long admin;
    private long route;
    private long vehicle;
    private long trip;
    private long firstSeat;
    private long secondSeat;
    private final List<Long> extraVehicles = new ArrayList<>();

    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock bookingClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    // Không dùng transaction ngoài test: mỗi request phải tự commit/rollback thật.
    @BeforeEach
    void prepare() {
        customer = account("CUSTOMER");
        otherCustomer = account("CUSTOMER");
        admin = account("ADMIN");
        route = jdbc.queryForObject(
                "INSERT INTO routes(origin,destination) VALUES ('P4 Origin','P4 Destination') RETURNING id", Long.class);
        vehicle = createVehicle();
        firstSeat = createSeat(vehicle, "A1");
        secondSeat = createSeat(vehicle, "A2");
        trip = jdbc.queryForObject("""
                INSERT INTO trips(route_id,vehicle_id,departure_at,arrival_at,base_price)
                VALUES (?,?,?,?,100000.25) RETURNING id
                """, Long.class, route, vehicle, time(NOW.plusSeconds(86400)), time(NOW.plusSeconds(93600)));
    }

    @AfterEach
    void cleanOwnFixtures() {
        jdbc.update("DELETE FROM booking_seats WHERE trip_id = ?", trip);
        jdbc.update("DELETE FROM bookings WHERE trip_id = ?", trip);
        jdbc.update("DELETE FROM trips WHERE id = ?", trip);
        jdbc.update("DELETE FROM seats WHERE vehicle_id = ?", vehicle);
        jdbc.update("DELETE FROM vehicles WHERE id = ?", vehicle);
        for (Long extraVehicle : extraVehicles) {
            jdbc.update("DELETE FROM seats WHERE vehicle_id = ?", extraVehicle);
            jdbc.update("DELETE FROM vehicles WHERE id = ?", extraVehicle);
        }
        jdbc.update("DELETE FROM routes WHERE id = ?", route);
        jdbc.update("DELETE FROM users WHERE id IN (?,?,?)", customer, otherCustomer, admin);
    }

    @Test
    void createUsesAuthenticatedOwnerDatabasePricesAndTenMinuteHold() throws Exception {
        JsonNode booking = create(customer, List.of(firstSeat, secondSeat));
        assertThat(booking.get("userId").asLong()).isEqualTo(customer);
        assertThat(booking.get("status").asText()).isEqualTo("PENDING");
        assertThat(booking.get("bookingCode").asText()).hasSize(32);
        assertThat(booking.get("totalAmount").decimalValue()).isEqualByComparingTo("200000.50");
        assertThat(booking.get("holdExpiresAt").asText()).isEqualTo(NOW.plusSeconds(600).toString());
        assertThat(booking.get("holdExpired").asBoolean()).isFalse();
        assertThat(booking.get("seats")).hasSize(2);
        for (JsonNode seat : booking.get("seats")) {
            assertThat(seat.get("unitPrice").decimalValue()).isEqualByComparingTo("100000.25");
            assertThat(seat.get("active").asBoolean()).isTrue();
        }
        mvc.perform(get("/api/v1/trips/" + trip + "/seats"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("HELD"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tickets", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"tripId\":0,\"seatIds\":[1]}", "{\"tripId\":1,\"seatIds\":[]}",
            "{\"tripId\":1,\"seatIds\":[null]}", "{\"tripId\":1,\"seatIds\":[0]}",
            "{\"tripId\":1,\"seatIds\":[1,2,3,4,5,6,7,8,9,10,11]}",
            "{\"tripId\":1,\"seatIds\":[1],\"userId\":2}",
            "{\"tripId\":1,\"seatIds\":[1],\"totalAmount\":1}",
            "{\"tripId\":1,\"seatIds\":[1],\"status\":\"PAID\"}"
    })
    void invalidOrClientControlledFieldsAreRejected(String body) throws Exception {
        mvc.perform(post(API).with(user(Long.toString(customer)).roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(bookingCount()).isZero();
    }

    @Test
    void duplicateMissingAndWrongVehicleSeatsDoNotCreateBooking() throws Exception {
        request(post(API), customer, "CUSTOMER", body(List.of(firstSeat, firstSeat)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DUPLICATE_SEAT"));
        request(post(API), customer, "CUSTOMER", body(List.of(Long.MAX_VALUE)))
                .andExpect(status().isNotFound());
        long extraVehicle = createVehicle();
        extraVehicles.add(extraVehicle);
        long foreignSeat = createSeat(extraVehicle, "B1");
        request(post(API), customer, "CUSTOMER", body(List.of(firstSeat, foreignSeat)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SEAT_WRONG_VEHICLE"));
        request(post(API), customer, "CUSTOMER", Map.of("tripId", Long.MAX_VALUE, "seatIds", List.of(firstSeat)))
                .andExpect(status().isNotFound());
        assertThat(bookingCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "COMPLETED", "DEPARTED", "INACTIVE_ROUTE", "INACTIVE_VEHICLE"})
    void closedTripsCannotBeBooked(String state) throws Exception {
        if ("DEPARTED".equals(state)) {
            jdbc.update("UPDATE trips SET departure_at = ? WHERE id = ?", time(NOW), trip);
        } else if ("INACTIVE_ROUTE".equals(state)) {
            jdbc.update("UPDATE routes SET active = false WHERE id = ?", route);
        } else if ("INACTIVE_VEHICLE".equals(state)) {
            jdbc.update("UPDATE vehicles SET active = false WHERE id = ?", vehicle);
        } else {
            jdbc.update("UPDATE trips SET status = ? WHERE id = ?", state, trip);
        }
        request(post(API), customer, "CUSTOMER", body(List.of(firstSeat)))
                .andExpect(status().isConflict());
        assertThat(bookingCount()).isZero();
    }

    @Test
    void holdNeverExtendsBeyondDepartureAndTotalMustFitDatabase() throws Exception {
        jdbc.update("UPDATE trips SET departure_at = ? WHERE id = ?", time(NOW.plusSeconds(45)), trip);
        JsonNode booking = create(customer, List.of(firstSeat));
        assertThat(booking.get("holdExpiresAt").asText()).isEqualTo(NOW.plusSeconds(45).toString());
        cancel(booking.get("id").asLong(), customer, "CUSTOMER").andExpect(status().isOk());
        jdbc.update("UPDATE trips SET base_price = 9999999999.99 WHERE id = ?", trip);
        request(post(API), customer, "CUSTOMER", body(List.of(firstSeat, secondSeat)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BOOKING_TOTAL_TOO_LARGE"));
        assertThat(bookingCount()).isEqualTo(1);
        assertThat(activeSeatCount()).isZero();
    }

    @Test
    void heldSeatConflictDoesNotReserveAnyOtherRequestedSeat() throws Exception {
        create(customer, List.of(firstSeat));
        request(post(API), otherCustomer, "CUSTOMER", body(List.of(secondSeat, firstSeat)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));
        assertThat(bookingCount()).isEqualTo(1);
        assertThat(activeSeatCount()).isEqualTo(1);
        create(otherCustomer, List.of(secondSeat));
    }

    @Test
    void customersOnlySeeOwnHistoryAndOwnerOrAdminCanReadAndCancel() throws Exception {
        long id = create(customer, List.of(firstSeat)).get("id").asLong();
        create(otherCustomer, List.of(secondSeat));
        mvc.perform(get(API + "/my").with(user(Long.toString(customer)).roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(API + "/" + id).with(user(Long.toString(otherCustomer)).roles("CUSTOMER")))
                .andExpect(status().isNotFound());
        cancel(id, otherCustomer, "CUSTOMER").andExpect(status().isNotFound());
        mvc.perform(get(API + "/" + id).with(user(Long.toString(admin)).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(customer));
        cancel(id, admin, "ADMIN").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/v1/admin/bookings").param("status", "CANCELLED")
                        .with(user(Long.toString(admin)).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void cancellationIsIdempotentAndReleasesEverySeatForReuse() throws Exception {
        long id = create(customer, List.of(firstSeat, secondSeat)).get("id").asLong();
        cancel(id, customer, "CUSTOMER").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.seats[0].active").value(false))
                .andExpect(jsonPath("$.seats[1].active").value(false));
        cancel(id, customer, "CUSTOMER").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(activeSeatCount()).isZero();
        create(otherCustomer, List.of(firstSeat, secondSeat));
        assertThat(bookingCount()).isEqualTo(2);
    }

    @Test
    void paidBookingCannotBeCancelledExpiredOrReservedAgain() throws Exception {
        long id = create(customer, List.of(firstSeat)).get("id").asLong();
        jdbc.update("UPDATE bookings SET status = 'PAID', hold_expires_at = ? WHERE id = ?", time(NOW), id);
        expiry.expireTrip(trip);
        cancel(id, customer, "CUSTOMER").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_ALREADY_PAID"));
        request(post(API), otherCustomer, "CUSTOMER", body(List.of(firstSeat)))
                .andExpect(status().isConflict());
        assertThat(activeSeatCount()).isEqualTo(1);
        assertThat(storedStatus(id)).isEqualTo("PAID");
    }

    @Test
    void expiryBoundaryReleasesEverySeatAndRepeatedRunsAreSafe() throws Exception {
        long id = create(customer, List.of(firstSeat, secondSeat)).get("id").asLong();
        jdbc.update("UPDATE bookings SET hold_expires_at = ? WHERE id = ?", time(NOW), id);
        expiry.expireTrip(trip);
        expiry.expireTrip(trip);
        assertThat(storedStatus(id)).isEqualTo("EXPIRED");
        assertThat(activeSeatCount()).isZero();
        cancel(id, customer, "CUSTOMER").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.holdExpired").value(true));
        create(otherCustomer, List.of(firstSeat, secondSeat));
    }

    @Test
    void bookingRequestReclaimsExpiredHoldsWithoutWaitingForScheduler() throws Exception {
        long id = create(customer, List.of(firstSeat)).get("id").asLong();
        jdbc.update("UPDATE bookings SET hold_expires_at = ? WHERE id = ?", time(NOW), id);
        mvc.perform(get(API + "/" + id).with(user(Long.toString(customer)).roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.holdExpired").value(true));
        // GET không ghi DB; POST giữ ghế mới thực hiện release trong transaction.
        assertThat(storedStatus(id)).isEqualTo("PENDING");
        create(otherCustomer, List.of(firstSeat));
        assertThat(storedStatus(id)).isEqualTo("EXPIRED");
        assertThat(activeSeatCount()).isEqualTo(1);
    }

    @Test
    void failedRequestRollsBackExpiryAndDoesNotCreatePartialBooking() throws Exception {
        long expiredId = create(customer, List.of(firstSeat)).get("id").asLong();
        create(otherCustomer, List.of(secondSeat));
        jdbc.update("UPDATE bookings SET hold_expires_at = ? WHERE id = ?", time(NOW), expiredId);
        request(post(API), otherCustomer, "CUSTOMER", body(List.of(firstSeat, secondSeat)))
                .andExpect(status().isConflict());
        assertThat(storedStatus(expiredId)).isEqualTo("PENDING");
        assertThat(activeSeatCount()).isEqualTo(2);
        assertThat(bookingCount()).isEqualTo(2);
        create(otherCustomer, List.of(firstSeat));
        assertThat(storedStatus(expiredId)).isEqualTo("EXPIRED");
        assertThat(activeSeatCount()).isEqualTo(2);
    }

    @Test
    void schedulerReleasesExpiredPendingButKeepsUnexpiredHolds() throws Exception {
        long expiredId = create(customer, List.of(firstSeat)).get("id").asLong();
        long validId = create(otherCustomer, List.of(secondSeat)).get("id").asLong();
        jdbc.update("UPDATE bookings SET hold_expires_at = ? WHERE id = ?", time(NOW), expiredId);
        BookingExpiryScheduler scheduler = new BookingExpiryScheduler(
                jdbc, expiry, Clock.fixed(NOW, ZoneOffset.UTC));
        scheduler.releaseExpiredHolds();
        assertThat(storedStatus(expiredId)).isEqualTo("EXPIRED");
        assertThat(storedStatus(validId)).isEqualTo("PENDING");
        assertThat(activeSeatCount()).isEqualTo(1);
    }

    @Test
    void anonymousAndWrongRoleCannotAccessBookings() throws Exception {
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body(List.of(firstSeat)))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(API + "/my")).andExpect(status().isUnauthorized());
        mvc.perform(get(API + "/1")).andExpect(status().isUnauthorized());
        mvc.perform(post(API + "/1/cancel")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/bookings")
                        .with(user(Long.toString(customer)).roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        request(post(API), customer, "OTHER", body(List.of(firstSeat)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101"})
    void invalidHistoryPaginationReturns400(String parameter) throws Exception {
        String[] parts = parameter.split("=");
        mvc.perform(get(API + "/my").param(parts[0], parts[1])
                        .with(user(Long.toString(customer)).roles("CUSTOMER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void twoCustomersCompetingForOneSeatProduceOneBookingAndOneConflict() throws Exception {
        competeForFirstSeat();
        assertThat(bookingCount()).isEqualTo(1);
        assertThat(activeSeatCount()).isEqualTo(1);
    }

    @Test
    void twoCustomersCompetingForExpiredSeatCannotBothAcquireIt() throws Exception {
        long oldId = create(customer, List.of(firstSeat)).get("id").asLong();
        jdbc.update("UPDATE bookings SET hold_expires_at = ? WHERE id = ?", time(NOW), oldId);
        competeForFirstSeat();
        assertThat(bookingCount()).isEqualTo(2);
        assertThat(activeSeatCount()).isEqualTo(1);
        assertThat(storedStatus(oldId)).isEqualTo("EXPIRED");
    }

    private void competeForFirstSeat() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> {
                start.await();
                return attemptBooking(customer);
            });
            Future<Integer> second = executor.submit(() -> {
                start.await();
                return attemptBooking(otherCustomer);
            });
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private int attemptBooking(long userId) throws Exception {
        return request(post(API), userId, "CUSTOMER", body(List.of(firstSeat)))
                .andReturn().getResponse().getStatus();
    }

    private JsonNode create(long userId, List<Long> seatIds) throws Exception {
        MvcResult result = request(post(API), userId, "CUSTOMER", body(seatIds))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private ResultActions cancel(long id, long userId, String role) throws Exception {
        return mvc.perform(post(API + "/" + id + "/cancel")
                .with(user(Long.toString(userId)).roles(role)));
    }

    private ResultActions request(MockHttpServletRequestBuilder request, long userId,
                                  String role, Object body) throws Exception {
        return mvc.perform(request.with(user(Long.toString(userId)).roles(role))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }

    private Map<String, Object> body(List<Long> seatIds) {
        return Map.of("tripId", trip, "seatIds", seatIds);
    }

    private long account(String role) {
        return jdbc.queryForObject("""
                INSERT INTO users(full_name,email,password_hash,role)
                VALUES ('P4 Test',?,'test-not-for-login',?) RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.invalid", role);
    }

    private long createVehicle() {
        return jdbc.queryForObject("""
                INSERT INTO vehicles(vehicle_code,vehicle_type,display_name)
                VALUES (?,'BUS','P4 Bus') RETURNING id
                """, Long.class, UUID.randomUUID().toString());
    }

    private long createSeat(long vehicleId, String code) {
        return jdbc.queryForObject(
                "INSERT INTO seats(vehicle_id,seat_code) VALUES (?,?) RETURNING id", Long.class, vehicleId, code);
    }

    private String storedStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, id);
    }

    private int bookingCount() {
        return jdbc.queryForObject("SELECT count(*) FROM bookings WHERE trip_id = ?", Integer.class, trip);
    }

    private int activeSeatCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM booking_seats WHERE trip_id = ? AND is_active", Integer.class, trip);
    }

    private java.time.OffsetDateTime time(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
