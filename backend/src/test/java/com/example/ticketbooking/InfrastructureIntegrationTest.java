package com.example.ticketbooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InfrastructureIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired DataSource dataSource;

    @Test
    void upgradeFromV1NormalizesExistingEmailWithoutChangingAccount() {
        String schema = "p2_upgrade_" + java.util.UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("1").load().migrate();
            jdbc.update("INSERT INTO " + schema + ".users(full_name,email,password_hash) VALUES (?,?,?)",
                    "Existing user", "  EXISTING@EXAMPLE.INVALID  ", "existing-hash");
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
            assertThat(jdbc.queryForObject("SELECT email FROM " + schema + ".users", String.class))
                    .isEqualTo("existing@example.invalid");
            assertThat(jdbc.queryForObject("SELECT password_hash FROM " + schema + ".users", String.class))
                    .isEqualTo("existing-hash");
        } finally { jdbc.execute("DROP SCHEMA " + schema + " CASCADE"); }
    }

    @Test
    void upgradeCollisionFailsAtomicallyAndPreservesBothExistingAccounts() {
        String schema = "p2_collision_" + java.util.UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("1").load().migrate();
            jdbc.update("INSERT INTO " + schema + ".users(full_name,email,password_hash) VALUES (?,?,?),(?,?,?)",
                    "First", "Dupe@example.invalid", "first-hash", "Second", "dupe@example.invalid", "second-hash");
            assertThatThrownBy(() -> Flyway.configure().dataSource(dataSource).schemas(schema)
                    .defaultSchema(schema).load().migrate()).isInstanceOf(FlywayException.class);
            assertThat(jdbc.queryForList("SELECT email FROM " + schema + ".users", String.class))
                    .containsExactlyInAnyOrder("Dupe@example.invalid", "dupe@example.invalid");
        } finally { jdbc.execute("DROP SCHEMA " + schema + " CASCADE"); }
    }

    @Test
    void migrationCreatesAllNineBusinessTables() {
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("4");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """, String.class)).containsExactlyInAnyOrder("users", "routes", "vehicles",
                "seats", "trips", "bookings", "booking_seats", "payments", "tickets");
        assertThat(jdbc.queryForObject("SHOW TIME ZONE", String.class)).isEqualTo("UTC");
    }

    @Test
    void healthIsPublicWithoutInternalDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void anonymousRequestsToOtherEndpointsAreDeniedAsJson() throws Exception {
        mvc.perform(get("/api/v1/bookings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotAccessAdminEndpoints() throws Exception {
        mvc.perform(get("/api/v1/admin/bookings"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @Transactional
    void activeSeatCannotBeReservedTwiceForTheSameTrip() {
        Fixture fixture = fixture();
        long first = booking(fixture);
        long second = booking(fixture);
        reserve(first, fixture.trip(), fixture.vehicle(), fixture.seat(), true);
        assertThatThrownBy(() -> reserve(second, fixture.trip(), fixture.vehicle(),
                fixture.seat(), true)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_active_trip_seat");
    }

    @Test
    @Transactional
    void releasedSeatCanBeReservedAgainAndUsedOnAnotherTrip() {
        Fixture fixture = fixture();
        long old = booking(fixture);
        reserve(old, fixture.trip(), fixture.vehicle(), fixture.seat(), true);
        jdbc.update("UPDATE bookings SET status = 'CANCELLED' WHERE id = ?", old);
        jdbc.update("UPDATE booking_seats SET is_active = false WHERE booking_id = ?", old);
        reserve(booking(fixture), fixture.trip(), fixture.vehicle(), fixture.seat(), true);
        long anotherTrip = trip(fixture.route(), fixture.vehicle());
        Fixture next = new Fixture(fixture.user(), fixture.route(), fixture.vehicle(),
                anotherTrip, fixture.seat());
        reserve(booking(next), next.trip(), next.vehicle(), next.seat(), true);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM booking_seats WHERE seat_id = ? AND is_active IS TRUE
                """, Integer.class, fixture.seat())).isEqualTo(2);
    }

    @Test
    @Transactional
    void compositeForeignKeyRejectsSeatFromAnotherVehicle() {
        Fixture fixture = fixture();
        long otherVehicle = id("""
                INSERT INTO vehicles(vehicle_code, vehicle_type, display_name)
                VALUES ('OTHER', 'BUS', 'Other bus') RETURNING id
                """);
        long otherSeat = id("""
                INSERT INTO seats(vehicle_id, seat_code) VALUES (?, 'A1') RETURNING id
                """, otherVehicle);
        long booking = booking(fixture);
        assertThatThrownBy(() -> reserve(booking, fixture.trip(), fixture.vehicle(), otherSeat, true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_bs_seat_vehicle");
    }

    @Test
    @Transactional
    void compositeForeignKeyRejectsBookingWithWrongTripVehicle() {
        Fixture fixture = fixture();
        long otherVehicle = id("""
                INSERT INTO vehicles(vehicle_code, vehicle_type, display_name)
                VALUES ('OTHER', 'BUS', 'Other bus') RETURNING id
                """);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO bookings(booking_code, user_id, trip_id, vehicle_id,
                                     hold_expires_at, total_amount)
                VALUES ('INVALID', ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '10 minutes', 100000)
                """, fixture.user(), fixture.trip(), otherVehicle))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_booking_trip_vehicle");
    }

    @Test
    @Transactional
    void tripTimeConstraintRejectsArrivalBeforeDeparture() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO trips(route_id, vehicle_id, departure_at, arrival_at, base_price)
                VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP - INTERVAL '1 hour', 100000)
                """, fixture.route(), fixture.vehicle()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_trip_time");
    }

    @Test
    @Transactional
    void ticketCanOnlyBeIssuedOnceForOneBookingSeat() {
        Fixture fixture = fixture();
        long bookingSeat = reserve(booking(fixture), fixture.trip(), fixture.vehicle(), fixture.seat(), true);
        jdbc.update("INSERT INTO tickets(ticket_code, booking_seat_id) VALUES ('TICKET1', ?)", bookingSeat);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO tickets(ticket_code, booking_seat_id) VALUES ('TICKET2', ?)", bookingSeat))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture fixture() {
        long user = id("""
                INSERT INTO users(full_name, email, password_hash)
                VALUES ('Test user', 'p1-test@example.invalid', 'test-fixture-not-a-login') RETURNING id
                """);
        long route = id("INSERT INTO routes(origin, destination) VALUES ('A', 'B') RETURNING id");
        long vehicle = id("""
                INSERT INTO vehicles(vehicle_code, vehicle_type, display_name)
                VALUES ('P1-TEST', 'BUS', 'Test bus') RETURNING id
                """);
        long seat = id("INSERT INTO seats(vehicle_id, seat_code) VALUES (?, 'A1') RETURNING id", vehicle);
        return new Fixture(user, route, vehicle, trip(route, vehicle), seat);
    }

    private long trip(long route, long vehicle) {
        return id("""
                INSERT INTO trips(route_id, vehicle_id, departure_at, arrival_at, base_price)
                VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day',
                        CURRENT_TIMESTAMP + INTERVAL '1 day 2 hours', 100000) RETURNING id
                """, route, vehicle);
    }

    private long booking(Fixture fixture) {
        return id("""
                INSERT INTO bookings(booking_code, user_id, trip_id, vehicle_id,
                                     hold_expires_at, total_amount)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '10 minutes', 100000) RETURNING id
                """, java.util.UUID.randomUUID().toString().replace("-", ""), fixture.user(),
                fixture.trip(), fixture.vehicle());
    }

    private long reserve(long booking, long trip, long vehicle, long seat, boolean active) {
        return id("""
                INSERT INTO booking_seats(booking_id, trip_id, vehicle_id, seat_id, unit_price, is_active)
                VALUES (?, ?, ?, ?, 100000, ?) RETURNING id
                """, booking, trip, vehicle, seat, active);
    }

    private long id(String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        if (value == null) throw new IllegalStateException("Missing generated id");
        return value;
    }

    private record Fixture(long user, long route, long vehicle, long trip, long seat) { }
}
