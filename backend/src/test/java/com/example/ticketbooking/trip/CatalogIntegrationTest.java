package com.example.ticketbooking.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CatalogIntegrationTest.FixedTime.class)
@WithMockUser(roles = "ADMIN")
@Transactional
class CatalogIntegrationTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final String ADMIN = "/api/v1/admin";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock catalogClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    @Test
    void adminCanCreateReadUpdateListAndDeleteUnusedCatalog() throws Exception {
        long route = create(ADMIN + "/routes", routeBody(" Hanoi ", " Hai Phong ", true));
        long vehicle = vehicle("BUS");
        long seat = create(ADMIN + "/vehicles/" + vehicle + "/seats", Map.of("seatCode", " a1 ", "seatClass", " standard "));
        mvc.perform(get(ADMIN + "/routes/" + route)).andExpect(status().isOk()).andExpect(jsonPath("$.origin").value("Hanoi"));
        mvc.perform(get(ADMIN + "/vehicles/" + vehicle + "/seats/" + seat)).andExpect(status().isOk()).andExpect(jsonPath("$.seatCode").value("A1"));
        request(put(ADMIN + "/routes/" + route), routeBody("Hanoi", "Hai Phong", false)).andExpect(status().isOk());
        request(put(ADMIN + "/routes/" + route), routeBody("Hanoi", "Hai Phong", true)).andExpect(status().isOk());
        request(put(ADMIN + "/vehicles/" + vehicle), vehicleBody("UPDATED-CODE", "BUS", true)).andExpect(status().isOk());
        request(put(ADMIN + "/vehicles/" + vehicle + "/seats/" + seat), Map.of("seatCode", "B1", "seatClass", "VIP")).andExpect(status().isOk());
        long trip = create(ADMIN + "/trips", tripBody(route, vehicle, "2030-01-02T01:00:00Z", "2030-01-02T03:00:00Z"));
        mvc.perform(get(ADMIN + "/trips/" + trip)).andExpect(status().isOk()).andExpect(jsonPath("$.vehicleCode").value("UPDATED-CODE"));
        Map<String,Object> update = tripBody(route, vehicle, "2030-01-02T02:00:00Z", "2030-01-02T04:00:00Z");
        update.put("basePrice", 250000.50);
        request(put(ADMIN + "/trips/" + trip), update).andExpect(status().isOk()).andExpect(jsonPath("$.basePrice").value(250000.50));
        for (String path : List.of("/routes", "/vehicles", "/trips", "/vehicles/" + vehicle + "/seats")) {
            mvc.perform(get(ADMIN + path).param("size", "1")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(1)).andExpect(jsonPath("$.totalElements").value(1));
        }
        mvc.perform(delete(ADMIN + "/trips/" + trip)).andExpect(status().isNoContent());
        mvc.perform(delete(ADMIN + "/vehicles/" + vehicle + "/seats/" + seat)).andExpect(status().isNoContent());
        mvc.perform(delete(ADMIN + "/vehicles/" + vehicle)).andExpect(status().isNoContent());
        mvc.perform(delete(ADMIN + "/routes/" + route)).andExpect(status().isNoContent());
        mvc.perform(get(ADMIN + "/trips/" + trip)).andExpect(status().isNotFound());
    }

    @Test
    void anonymousAndCustomerCannotUseAnyAdminResource() throws Exception {
        for (String path : List.of("/routes", "/vehicles", "/vehicles/1/seats", "/trips")) {
            mvc.perform(get(ADMIN + path).with(anonymous())).andExpect(status().isUnauthorized());
            mvc.perform(get(ADMIN + path).with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
            request(post(ADMIN + path).with(user("customer").roles("CUSTOMER")), Map.of()).andExpect(status().isForbidden());
        }
        request(post("/api/v1/trips").with(anonymous()), Map.of()).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateVehicleAndSeatCodesAreCaseInsensitiveAndNestedSeatIsScoped() throws Exception {
        long vehicle = create(ADMIN + "/vehicles", vehicleBody(" bus-01 ", "BUS", true));
        request(post(ADMIN + "/vehicles"), vehicleBody("BUS-01", "BUS", true)).andExpect(status().isConflict());
        long seat = create(ADMIN + "/vehicles/" + vehicle + "/seats", Map.of("seatCode", "A1", "seatClass", "STANDARD"));
        request(post(ADMIN + "/vehicles/" + vehicle + "/seats"), Map.of("seatCode", " a1 ", "seatClass", "VIP")).andExpect(status().isConflict());
        long other = vehicle("TRAIN");
        mvc.perform(get(ADMIN + "/vehicles/" + other + "/seats/" + seat)).andExpect(status().isNotFound());
    }

    @Test
    void routeAndVehicleReferencesBlockDeletionAndEndpointOrTypeChanges() throws Exception {
        Fixture f = fixture();
        mvc.perform(delete(ADMIN + "/routes/" + f.route())).andExpect(status().isConflict());
        mvc.perform(delete(ADMIN + "/vehicles/" + f.vehicle())).andExpect(status().isConflict());
        request(put(ADMIN + "/routes/" + f.route()), routeBody("Changed", "Hai Phong", true)).andExpect(status().isConflict());
        request(put(ADMIN + "/vehicles/" + f.vehicle()), vehicleBody("NEW", "TRAIN", true)).andExpect(status().isConflict());
        request(put(ADMIN + "/routes/" + f.route()), routeBody("Hanoi", "Hai Phong", false)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/trips/" + f.trip()).with(anonymous())).andExpect(status().isNotFound());
        mvc.perform(get(ADMIN + "/trips/" + f.trip())).andExpect(status().isOk());
    }

    @Test
    void bookingHistoryBlocksTripChangesAndAllSeatLayoutMutations() throws Exception {
        Fixture f = fixture();
        book(f, f.seat(), "CANCELLED", NOW.minusSeconds(1), false);
        request(put(ADMIN + "/trips/" + f.trip()), tripBody(f.route(), f.vehicle(), "2030-01-02T01:00:00Z", "2030-01-02T03:00:00Z"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TRIP_HAS_BOOKINGS"));
        mvc.perform(delete(ADMIN + "/trips/" + f.trip())).andExpect(status().isConflict());
        request(post(ADMIN + "/vehicles/" + f.vehicle() + "/seats"), Map.of("seatCode", "C1", "seatClass", "STANDARD"))
                .andExpect(status().isConflict());
        request(put(ADMIN + "/vehicles/" + f.vehicle() + "/seats/" + f.seat()), Map.of("seatCode", "C1", "seatClass", "STANDARD"))
                .andExpect(status().isConflict());
        mvc.perform(delete(ADMIN + "/vehicles/" + f.vehicle() + "/seats/" + f.seat())).andExpect(status().isConflict());
    }

    @Test
    void publicSearchUsesVietnamMidnightBoundariesAndStablePagination() throws Exception {
        Fixture f = fixture(); // 2030-01-02 08:00 Vietnam
        long before = rawTrip(f, "2030-01-01T16:59:59Z", "2030-01-01T17:00:00Z", "SCHEDULED");
        long first = rawTrip(f, "2030-01-01T17:00:00Z", "2030-01-01T18:00:00Z", "SCHEDULED");
        long end = rawTrip(f, "2030-01-02T16:59:59Z", "2030-01-02T17:00:00Z", "SCHEDULED");
        long next = rawTrip(f, "2030-01-02T17:00:00Z", "2030-01-02T18:00:00Z", "SCHEDULED");
        JsonNode results = json(mvc.perform(get("/api/v1/trips").with(anonymous())
                        .param("origin", " hAnOi ").param("destination", "hai phong").param("date", "2030-01-02"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3)).andReturn());
        assertThat(results.get("content").findValuesAsText("id"))
                .containsExactly(Long.toString(first), Long.toString(f.trip()), Long.toString(end))
                .doesNotContain(Long.toString(before), Long.toString(next));
        mvc.perform(get("/api/v1/trips").with(anonymous()).param("date", "2030-01-02").param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(f.trip()))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void publicSearchHidesDepartedCancelledCompletedAndInactiveVehicles() throws Exception {
        Fixture f = fixture();
        rawTrip(f, "2029-12-31T01:00:00Z", "2029-12-31T03:00:00Z", "SCHEDULED");
        rawTrip(f, "2030-01-03T01:00:00Z", "2030-01-03T03:00:00Z", "CANCELLED");
        rawTrip(f, "2029-12-30T01:00:00Z", "2029-12-30T03:00:00Z", "COMPLETED");
        mvc.perform(get("/api/v1/trips").with(anonymous())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        request(put(ADMIN + "/vehicles/" + f.vehicle()), vehicleBody("INACTIVE", "BUS", false)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/trips").with(anonymous())).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/trips/" + f.trip()).with(anonymous())).andExpect(status().isNotFound());
        mvc.perform(get(ADMIN + "/trips").param("status", "CANCELLED")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void publicSeatsExposeOnlyAvailabilityAndDoNotReleaseExpiredHolds() throws Exception {
        Fixture f = fixture();
        long held = seat(f.vehicle(), "A2"); long expired = seat(f.vehicle(), "A3"); long cancelled = seat(f.vehicle(), "A4");
        book(f, f.seat(), "PAID", NOW.minusSeconds(1), true);
        book(f, held, "PENDING", NOW.plusSeconds(600), true);
        book(f, expired, "PENDING", NOW.minusSeconds(1), true);
        book(f, cancelled, "CANCELLED", NOW.minusSeconds(1), false);
        JsonNode result = json(mvc.perform(get("/api/v1/trips/" + f.trip() + "/seats").with(anonymous()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("BOOKED"))
                .andExpect(jsonPath("$[1].status").value("HELD"))
                .andExpect(jsonPath("$[2].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[3].status").value("AVAILABLE")).andReturn());
        assertThat(result.toString()).doesNotContain("userId", "bookingId", "email");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_seats WHERE trip_id=? AND is_active", Integer.class, f.trip())).isEqualTo(3);
    }

    @Test
    void scheduleOverlapIsRejectedButTouchingIntervalsAndOtherVehiclesAreAllowed() throws Exception {
        Fixture f = fixture();
        request(post(ADMIN + "/trips"), tripBody(f.route(), f.vehicle(), "2030-01-02T02:00:00Z", "2030-01-02T04:00:00Z"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VEHICLE_SCHEDULE_CONFLICT"));
        create(ADMIN + "/trips", tripBody(f.route(), f.vehicle(), "2030-01-02T03:00:00Z", "2030-01-02T05:00:00Z"));
        long other = vehicle("TRAIN"); seat(other, "A1");
        create(ADMIN + "/trips", tripBody(f.route(), other, "2030-01-02T01:00:00Z", "2030-01-02T03:00:00Z"));
        mvc.perform(get("/api/v1/trips").with(anonymous()).param("vehicleType", "TRAIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void cancellationFreesScheduleAndCannotBeReopened() throws Exception {
        Fixture f = fixture();
        Map<String,Object> body = tripBody(f.route(), f.vehicle(), "2030-01-02T01:00:00Z", "2030-01-02T03:00:00Z");
        body.put("status", "CANCELLED"); request(put(ADMIN + "/trips/" + f.trip()), body).andExpect(status().isOk());
        body.put("status", "SCHEDULED"); request(put(ADMIN + "/trips/" + f.trip()), body).andExpect(status().isConflict());
        create(ADMIN + "/trips", body);
    }

    @Test
    void completedTripMustHaveArrivedAndBecomesImmutable() throws Exception {
        Fixture f = fixture();
        long past = rawTrip(f, "2029-12-31T01:00:00Z", "2029-12-31T03:00:00Z", "SCHEDULED");
        Map<String,Object> body = tripBody(f.route(), f.vehicle(), "2029-12-31T01:00:00Z", "2030-01-01T03:00:00Z");
        body.put("status", "COMPLETED");
        request(put(ADMIN + "/trips/" + past), body).andExpect(status().isConflict());
        body.put("arrivalAt", "2029-12-31T03:00:00Z");
        request(put(ADMIN + "/trips/" + past), body).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/v1/trips/" + past).with(anonymous())).andExpect(status().isNotFound());
        request(put(ADMIN + "/trips/" + past), body).andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "date=invalid", "vehicleType=PLANE", "origin=", "date=2030-99-99"})
    void invalidSearchParametersReturn400(String parameter) throws Exception {
        String[] pair = parameter.split("=", -1);
        mvc.perform(get("/api/v1/trips").with(anonymous()).param(pair[0], pair[1])).andExpect(status().isBadRequest());
    }

    @Test
    void invalidCatalogRequestsReturn400Or404WithoutSqlLeaks() throws Exception {
        request(post(ADMIN + "/routes"), routeBody("Same", "same", true)).andExpect(status().isBadRequest());
        request(post(ADMIN + "/vehicles"), vehicleBody("BAD", "PLANE", true)).andExpect(status().isBadRequest());
        mvc.perform(get(ADMIN + "/vehicles/0")).andExpect(status().isBadRequest());
        mvc.perform(get(ADMIN + "/vehicles/99999999")).andExpect(status().isNotFound());
        Fixture f = fixture();
        Map<String,Object> body = tripBody(f.route(), f.vehicle(), "2030-01-03T01:00:00Z", "2030-01-03T01:00:00Z");
        request(post(ADMIN + "/trips"), body).andExpect(status().isBadRequest());
        body = tripBody(f.route(), f.vehicle(), "2029-12-31T01:00:00Z", "2029-12-31T03:00:00Z");
        request(post(ADMIN + "/trips"), body).andExpect(status().isBadRequest());
        for (double price : List.of(-1.0, 1.001, 10000000000.0)) {
            body = tripBody(f.route(), f.vehicle(), "2030-01-03T01:00:00Z", "2030-01-03T03:00:00Z"); body.put("basePrice", price);
            request(post(ADMIN + "/trips"), body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.basePrice").exists());
        }
        body = tripBody(99999999L, f.vehicle(), "2030-01-03T01:00:00Z", "2030-01-03T03:00:00Z");
        request(post(ADMIN + "/trips"), body).andExpect(status().isNotFound());
    }

    @Test
    void inactiveCatalogSeatlessVehicleAndPrematureCompletionAreRejected() throws Exception {
        Fixture f = fixture();
        long emptyVehicle = vehicle("BUS");
        request(post(ADMIN + "/trips"), tripBody(f.route(), emptyVehicle, "2030-01-03T01:00:00Z", "2030-01-03T03:00:00Z"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VEHICLE_HAS_NO_SEATS"));
        Map<String,Object> body = tripBody(f.route(), f.vehicle(), "2030-01-02T01:00:00Z", "2030-01-02T03:00:00Z"); body.put("status", "COMPLETED");
        request(put(ADMIN + "/trips/" + f.trip()), body).andExpect(status().isConflict());
        request(put(ADMIN + "/routes/" + f.route()), routeBody("Hanoi", "Hai Phong", false)).andExpect(status().isOk());
        request(post(ADMIN + "/trips"), tripBody(f.route(), f.vehicle(), "2030-01-03T01:00:00Z", "2030-01-03T03:00:00Z"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATALOG_INACTIVE"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentScheduleRequestsCannotAssignSameVehicleTwice() throws Exception {
        long route = create(ADMIN + "/routes", routeBody("Race origin", "Race destination", true));
        long vehicle = vehicle("BUS"); seat(vehicle, "A1");
        String body = mapper.writeValueAsString(tripBody(route, vehicle, "2030-01-04T01:00:00Z", "2030-01-04T03:00:00Z"));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> task = () -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("No race start");
                return mvc.perform(post(ADMIN + "/trips").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
            };
            var first = executor.submit(task); var second = executor.submit(task); start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM trips WHERE vehicle_id=?", Integer.class, vehicle)).isEqualTo(1);
        } finally {
            start.countDown();
            jdbc.update("DELETE FROM trips WHERE vehicle_id=?", vehicle); jdbc.update("DELETE FROM seats WHERE vehicle_id=?", vehicle);
            jdbc.update("DELETE FROM vehicles WHERE id=?", vehicle); jdbc.update("DELETE FROM routes WHERE id=?", route);
        }
    }

    private Fixture fixture() throws Exception {
        long route = create(ADMIN + "/routes", routeBody("Hanoi", "Hai Phong", true));
        long vehicle = vehicle("BUS"); long seat = seat(vehicle, "A1");
        long trip = create(ADMIN + "/trips", tripBody(route, vehicle, "2030-01-02T01:00:00Z", "2030-01-02T03:00:00Z"));
        return new Fixture(route, vehicle, seat, trip);
    }
    private long vehicle(String type) throws Exception { return create(ADMIN + "/vehicles", vehicleBody(UUID.randomUUID().toString(), type, true)); }
    private long seat(long vehicle, String code) throws Exception { return create(ADMIN + "/vehicles/" + vehicle + "/seats", Map.of("seatCode", code, "seatClass", "STANDARD")); }
    private long create(String path, Object body) throws Exception { return json(request(post(path), body).andExpect(status().isCreated()).andReturn()).get("id").asLong(); }
    private org.springframework.test.web.servlet.ResultActions request(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }
    private JsonNode json(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()); }
    private Map<String,Object> routeBody(String origin, String destination, boolean active) { return Map.of("origin", origin, "destination", destination, "active", active); }
    private Map<String,Object> vehicleBody(String code, String type, boolean active) { return Map.of("vehicleCode", code, "vehicleType", type, "displayName", "Test vehicle", "active", active); }
    private Map<String,Object> tripBody(long route, long vehicle, String departure, String arrival) {
        return new HashMap<>(Map.of("routeId", route, "vehicleId", vehicle, "departureAt", departure, "arrivalAt", arrival, "basePrice", 100000, "status", "SCHEDULED"));
    }
    private long rawTrip(Fixture f, String departure, String arrival, String status) {
        return jdbc.queryForObject("INSERT INTO trips(route_id,vehicle_id,departure_at,arrival_at,base_price,status) VALUES (?,?,?,?,100000,?) RETURNING id",
                Long.class, f.route(), f.vehicle(), Instant.parse(departure).atOffset(ZoneOffset.UTC), Instant.parse(arrival).atOffset(ZoneOffset.UTC), status);
    }
    private void book(Fixture f, long seat, String status, Instant expiry, boolean active) {
        long account = jdbc.queryForObject("INSERT INTO users(full_name,email,password_hash) VALUES ('Fixture',?,'fixture-not-for-login') RETURNING id",
                Long.class, UUID.randomUUID() + "@example.invalid");
        long booking = jdbc.queryForObject("INSERT INTO bookings(booking_code,user_id,trip_id,vehicle_id,status,hold_expires_at,total_amount) VALUES (?,?,?,?,?,?,100000) RETURNING id",
                Long.class, UUID.randomUUID().toString().replace("-", ""), account, f.trip(), f.vehicle(), status, expiry.atOffset(ZoneOffset.UTC));
        jdbc.update("INSERT INTO booking_seats(booking_id,trip_id,vehicle_id,seat_id,unit_price,is_active) VALUES (?,?,?,?,100000,?)",
                booking, f.trip(), f.vehicle(), seat, active);
    }
    private record Fixture(long route, long vehicle, long seat, long trip) { }
}
