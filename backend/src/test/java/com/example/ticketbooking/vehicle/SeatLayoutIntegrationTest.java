package com.example.ticketbooking.vehicle;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

import com.example.ticketbooking.payment.VnpayGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"vnpay.enabled=true", "vnpay.tmn-code=LAYOUT01",
        "vnpay.hash-secret=test-only-layout-merchant-secret-32-characters"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SeatLayoutIntegrationTest.FixedTime.class)
@WithMockUser(roles = "ADMIN")
@Transactional
class SeatLayoutIntegrationTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired VnpayGateway gateway;

    @TestConfiguration
    static class FixedTime { @Bean @Primary Clock seatClock() { return Clock.fixed(NOW, ZoneOffset.UTC); } }

    @Test
    void busHasTwoDecksWithSeventeenBedsAndGenerationKeepsIdsOnRetry() throws Exception {
        long vehicle = vehicle("BUS");
        generate(vehicle);
        var ids = jdbc.queryForList("select id from seats where vehicle_id=? order by id", Long.class, vehicle);
        assertThat(ids).hasSize(34);
        generate(vehicle);
        assertThat(jdbc.queryForList("select id from seats where vehicle_id=? order by id", Long.class, vehicle)).isEqualTo(ids);
        for (int deck = 1; deck <= 2; deck++) {
            assertThat(jdbc.queryForObject("select count(*) from seats where vehicle_id=? and coach_number=0 and deck_number=? and seat_class='BUS_SLEEPER'", Long.class, vehicle, deck)).isEqualTo(17);
        }
        mvc.perform(post("/api/v1/admin/vehicles/" + vehicle + "/seats").contentType(MediaType.APPLICATION_JSON)
                .content("{\"seatCode\":\"A35\",\"seatClass\":\"STANDARD\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STANDARD_LAYOUT_LOCKED"));
        mvc.perform(delete("/api/v1/admin/vehicles/" + vehicle + "/seats/" + ids.getFirst()))
                .andExpect(status().isConflict());
    }

    @Test
    void trainHasElevenCoachesWithCorrectCapacityCompartmentAndDeck() throws Exception {
        long vehicle = vehicle("TRAIN");
        generate(vehicle);
        assertThat(jdbc.queryForObject("select count(*) from seats where vehicle_id=?", Long.class, vehicle)).isEqualTo(434);
        assertThat(jdbc.queryForObject("select count(distinct coach_number) from seats where vehicle_id=?", Long.class, vehicle)).isEqualTo(11);
        for (int coach = 1; coach <= 11; coach++) {
            long expected = coach <= 2 ? 56 : coach <= 6 ? 28 : 42;
            assertThat(jdbc.queryForObject("select count(*) from seats where vehicle_id=? and coach_number=?", Long.class, vehicle, coach)).isEqualTo(expected);
        }
        assertThat(jdbc.queryForMap("select compartment_number,deck_number from seats where vehicle_id=? and seat_code='T03-28'", vehicle))
                .containsEntry("compartment_number", 7).containsEntry("deck_number", 2);
        assertThat(jdbc.queryForMap("select compartment_number,deck_number from seats where vehicle_id=? and seat_code='T11-42'", vehicle))
                .containsEntry("compartment_number", 7).containsEntry("deck_number", 3);
        long trip = trip(vehicle);
        mvc.perform(get("/api/v1/trips/" + trip + "/seats").with(anonymous()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(434))
                .andExpect(jsonPath("$[0].coachNumber").value(1)).andExpect(jsonPath("$[0].positionNumber").value(1));
    }

    @Test
    void aPaidBookingCanContainDifferentCoachesAndTicketsRetainLocations() throws Exception {
        long vehicle = vehicle("TRAIN");
        generate(vehicle);
        long trip = trip(vehicle);
        long owner = account();
        List<Long> selected = jdbc.queryForList("select id from seats where vehicle_id=? and seat_code in ('T01-01','T03-01','T07-01') order by id", Long.class, vehicle);
        var booking = mvc.perform(post("/api/v1/bookings").with(user("" + owner).roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("tripId", trip, "seatIds", selected))))
                .andExpect(status().isCreated()).andReturn();
        long id = mapper.readTree(booking.getResponse().getContentAsString()).get("id").asLong();
        var payment = mvc.perform(post("/api/v1/bookings/" + id + "/payments").with(user("" + owner).roles("CUSTOMER")))
                .andExpect(status().isOk()).andReturn();
        String ref = mapper.readTree(payment.getResponse().getContentAsString()).get("txnRef").asText();
        Map<String,String> fields = new TreeMap<>();
        fields.put("vnp_TmnCode", "LAYOUT01"); fields.put("vnp_TxnRef", ref);
        fields.put("vnp_Amount", "45000000"); fields.put("vnp_ResponseCode", "00");
        fields.put("vnp_TransactionStatus", "00"); fields.put("vnp_TransactionNo", "987654321");
        fields.put("vnp_PayDate", VnpayGateway.format(NOW));
        var request = get("/api/v1/payments/vnpay/ipn").with(anonymous());
        for (var field : fields.entrySet()) request.param(field.getKey(), field.getValue());
        request.param("vnp_SecureHash", gateway.sign(fields));
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("00"));
        var tickets = mvc.perform(get("/api/v1/bookings/" + id + "/tickets").with(user("" + owner).roles("CUSTOMER")))
                .andExpect(status().isOk()).andReturn();
        var result = mapper.readTree(tickets.getResponse().getContentAsString());
        assertThat(result.size()).isEqualTo(3);
        Set<Integer> coaches = new HashSet<>();
        for (var ticket : result) {
            coaches.add(ticket.get("coachNumber").asInt());
            assertThat(ticket.get("positionNumber").asInt()).isEqualTo(1);
        }
        assertThat(coaches).containsExactlyInAnyOrder(1, 3, 7);
        generate(vehicle); // Idempotent, even with history. Does not delete/recreate booked seats.
        assertThat(jdbc.queryForObject("select count(*) from tickets", Long.class)).isEqualTo(3);
    }

    @Test
    void legacySeatsWithBookingHistoryCannotBeReplaced() throws Exception {
        long vehicle = vehicle("BUS");
        Long seat = jdbc.queryForObject("insert into seats(vehicle_id,seat_code) values(?,'A1') returning id", Long.class, vehicle);
        long trip = trip(vehicle);
        long owner = account();
        mvc.perform(post("/api/v1/bookings").with(user("" + owner).roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("tripId", trip, "seatIds", List.of(seat)))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/admin/vehicles/" + vehicle + "/seat-layout"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SEATS_IN_USE"));
        assertThat(jdbc.queryForList("select id from seats where vehicle_id=?", Long.class, vehicle)).containsExactly(seat);
    }

    @Test
    void anonymousAndCustomerCannotGenerateLayouts() throws Exception {
        long vehicle = vehicle("TRAIN");
        mvc.perform(post("/api/v1/admin/vehicles/" + vehicle + "/seat-layout").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/vehicles/" + vehicle + "/seat-layout").with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from seats where vehicle_id=?", Long.class, vehicle)).isZero();
    }

    private long vehicle(String type) {
        return jdbc.queryForObject("insert into vehicles(vehicle_code,vehicle_type,display_name) values(?,?,'Seat layout test') returning id", Long.class, UUID.randomUUID().toString(), type);
    }
    private void generate(long id) throws Exception {
        mvc.perform(post("/api/v1/admin/vehicles/" + id + "/seat-layout")).andExpect(status().isOk());
    }
    private long account() {
        return jdbc.queryForObject("insert into users(full_name,email,password_hash,email_verified) values('Seat customer',?,'unused',TRUE) returning id", Long.class, UUID.randomUUID() + "@example.invalid");
    }
    private long trip(long vehicle) {
        Long route = jdbc.queryForObject("insert into routes(origin,destination) values('Seat origin','Seat destination') returning id", Long.class);
        return jdbc.queryForObject("insert into trips(route_id,vehicle_id,departure_at,arrival_at,base_price) values(?,?,?, ?,150000) returning id", Long.class, route, vehicle,
                java.sql.Timestamp.from(NOW.plusSeconds(86400)), java.sql.Timestamp.from(NOW.plusSeconds(90000)));
    }
}
