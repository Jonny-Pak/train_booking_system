package com.example.ticketbooking;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Only scripts/Test-MigrationSafety.ps1 can opt into these isolated databases. */
@EnabledIfEnvironmentVariable(named = "MIGRATION_AUDIT_ALLOWED", matches = "true")
class MigrationSafetyTest {
    private DataSource source(String name) {
        String prefix = System.getenv("MIGRATION_AUDIT_URL_PREFIX");
        if (prefix == null || !prefix.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{4,5}/migration_audit_")) {
            throw new IllegalStateException("Only an explicit loopback audit database is allowed");
        }
        String port = prefix.split(":")[3].split("/")[0];
        if ("5432".equals(port)) throw new IllegalStateException("Never use the existing PostgreSQL port");
        return new DriverManagerDataSource(prefix + name,
                System.getenv("MIGRATION_AUDIT_USER"), System.getenv("MIGRATION_AUDIT_PASSWORD"));
    }

    private Flyway flyway(DataSource source, String target) {
        return Flyway.configure().dataSource(source).target(target).cleanDisabled(true)
                .baselineOnMigrate(false).validateOnMigrate(true).load();
    }

    @Test
    void emptyDatabaseMigratesAllTenWithOrdinaryOwnerAndRestartDoesNotSeedBusinessData() {
        DataSource source = source("fresh");
        var jdbc = new JdbcTemplate(source);
        var migrations = flyway(source, "10");
        assertThat(migrations.migrate().migrationsExecuted).isEqualTo(10);
        assertThat(migrations.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Long.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'", Long.class)).isEqualTo(11);
        for (String table : List.of("users", "routes", "vehicles", "seats", "trips", "bookings", "booking_seats", "payments", "tickets")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class)).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM site_settings", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT rolsuper, rolcreatedb, rolcreaterole FROM pg_roles WHERE rolname=current_user"))
                .containsEntry("rolsuper", false).containsEntry("rolcreatedb", false).containsEntry("rolcreaterole", false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE connamespace='public'::regnamespace AND NOT convalidated", Long.class)).isZero();
        assertThat(migrations.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM site_settings", Long.class)).isEqualTo(1);
    }

    @Test
    void v8AndV9ReplaceOnlyChecksAndKeepBookedSeatIdsAndHistory() {
        DataSource source = source("upgrade");
        var jdbc = new JdbcTemplate(source);
        flyway(source, "7").migrate();
        long bus = vehicle(jdbc, "BUS_16", "BUS");
        busSeats(jdbc, bus, 16);
        long train = vehicle(jdbc, "TRAIN_11", "TRAIN");
        jdbc.update("INSERT INTO seats(vehicle_id,seat_code,seat_class,coach_number,deck_number,position_number) VALUES (?, 'T01-01','SEATED',1,1,1)", train);
        var oldSeats = jdbc.queryForList("SELECT id,seat_code FROM seats WHERE vehicle_id=? ORDER BY id", bus);
        var oldTrain = jdbc.queryForList("SELECT * FROM seats WHERE vehicle_id=? ORDER BY id", train);
        long booking = booking(jdbc, bus);
        long reserved = id(jdbc, "INSERT INTO booking_seats(booking_id,trip_id,vehicle_id,seat_id,unit_price,is_active) SELECT id,trip_id,vehicle_id,(SELECT id FROM seats WHERE vehicle_id=? AND seat_code='A4'),100000,true FROM bookings WHERE id=? RETURNING id", bus, booking);
        jdbc.update("INSERT INTO payments(booking_id,txn_ref,amount,status,created_at,expires_at,provider_transaction_id) VALUES (?,'audit-paid',100000,'SUCCESS',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '10 minutes','100001')", booking);
        jdbc.update("INSERT INTO tickets(ticket_code,booking_seat_id) VALUES ('audit-ticket',?)", reserved);
        var bookings = jdbc.queryForList("SELECT * FROM bookings ORDER BY id");
        var bookingSeats = jdbc.queryForList("SELECT * FROM booking_seats ORDER BY id");
        var payments = jdbc.queryForList("SELECT * FROM payments ORDER BY id");
        var tickets = jdbc.queryForList("SELECT * FROM tickets ORDER BY id");

        assertThat(flyway(source, "8").migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seats WHERE vehicle_id=?", Long.class, bus)).isEqualTo(34);
        assertThat(jdbc.queryForObject("SELECT seat_layout FROM vehicles WHERE id=?", String.class, bus)).isEqualTo("BUS_34");
        assertThat(constraint(jdbc, "chk_vehicle_seat_layout")).contains("BUS_34").doesNotContain("BUS_16");
        assertThat(constraint(jdbc, "chk_seat_position")).contains("STANDARD", "34");

        assertThat(flyway(source, "10").migrate().migrationsExecuted).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT seat_layout FROM vehicles WHERE id=?", String.class, bus)).isEqualTo("BUS_SLEEPER_34");
        assertThat(jdbc.queryForList("SELECT id,seat_code FROM seats WHERE vehicle_id=? ORDER BY id LIMIT 16", bus)).isEqualTo(oldSeats);
        for (int deck = 1; deck <= 2; deck++) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM seats WHERE vehicle_id=? AND seat_class='BUS_SLEEPER' AND deck_number=?", Long.class, bus, deck)).isEqualTo(17);
        }
        assertThat(jdbc.queryForList("SELECT * FROM seats WHERE vehicle_id=? ORDER BY id", train)).isEqualTo(oldTrain);
        assertThat(jdbc.queryForList("SELECT * FROM bookings ORDER BY id")).isEqualTo(bookings);
        assertThat(jdbc.queryForList("SELECT * FROM booking_seats ORDER BY id")).isEqualTo(bookingSeats);
        assertThat(jdbc.queryForList("SELECT * FROM payments ORDER BY id")).isEqualTo(payments);
        assertThat(jdbc.queryForList("SELECT * FROM tickets ORDER BY id")).isEqualTo(tickets);
        assertThat(constraint(jdbc, "chk_vehicle_seat_layout")).contains("BUS_SLEEPER_34", "TRAIN_11", "CUSTOM").doesNotContain("BUS_16");
        assertThat(constraint(jdbc, "chk_seat_position")).contains("BUS_SLEEPER");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conname IN ('fk_booking_trip_vehicle','fk_bs_booking_trip_vehicle','fk_bs_seat_vehicle') AND convalidated", Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname='public' AND indexname='uq_active_trip_seat'", String.class))
                .contains("UNIQUE", "trip_id", "seat_id", "WHERE (is_active IS TRUE)");
        assertThat(flyway(source, "10").validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void inconsistentV8InputFailsBeforeDroppingChecksAndRollsBack() {
        DataSource source = source("bad_v8");
        var jdbc = new JdbcTemplate(source);
        flyway(source, "7").migrate();
        long bus = vehicle(jdbc, "BUS_16", "BUS");
        busSeats(jdbc, bus, 15);
        String oldCheck = constraint(jdbc, "chk_seat_position");
        assertThatThrownBy(() -> flyway(source, "8").migrate()).isInstanceOf(FlywayException.class);
        assertThat(flyway(source, "7").info().current().getVersion().toString()).isEqualTo("7");
        assertThat(constraint(jdbc, "chk_seat_position")).isEqualTo(oldCheck);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seats WHERE vehicle_id=?", Long.class, bus)).isEqualTo(15);
    }

    @Test
    void inconsistentV9InputFailsBeforeDroppingChecksAndRollsBack() {
        DataSource source = source("bad_v9");
        var jdbc = new JdbcTemplate(source);
        flyway(source, "8").migrate();
        long bus = vehicle(jdbc, "BUS_34", "BUS");
        busSeats(jdbc, bus, 33);
        String oldCheck = constraint(jdbc, "chk_seat_position");
        assertThatThrownBy(() -> flyway(source, "9").migrate()).isInstanceOf(FlywayException.class);
        assertThat(flyway(source, "8").info().current().getVersion().toString()).isEqualTo("8");
        assertThat(constraint(jdbc, "chk_seat_position")).isEqualTo(oldCheck);
        assertThat(jdbc.queryForObject("SELECT seat_layout FROM vehicles WHERE id=?", String.class, bus)).isEqualTo("BUS_34");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seats WHERE vehicle_id=?", Long.class, bus)).isEqualTo(33);
    }

    @Test
    void existingEmailCollisionInV2DoesNotDeleteOrMergeAccounts() {
        DataSource source = source("bad_v2");
        var jdbc = new JdbcTemplate(source);
        flyway(source, "1").migrate();
        jdbc.update("INSERT INTO users(full_name,email,password_hash) VALUES ('A','User@example.invalid','fixture'),('B',' user@example.invalid ','fixture')");
        var original = jdbc.queryForList("SELECT * FROM users ORDER BY id");
        assertThatThrownBy(() -> flyway(source, "2").migrate()).isInstanceOf(FlywayException.class);
        assertThat(jdbc.queryForList("SELECT * FROM users ORDER BY id")).isEqualTo(original);
        assertThat(flyway(source, "1").info().current().getVersion().toString()).isEqualTo("1");
    }

    @Test
    void v8FailureAfterDroppingChecksRestoresConstraintsAndOriginalRows() {
        DataSource source = source("bad_v8_codes");
        var jdbc = new JdbcTemplate(source);
        flyway(source, "7").migrate();
        long bus = vehicle(jdbc, "BUS_16", "BUS");
        busSeats(jdbc, bus, 16);
        // Coordinates are valid, but A17 was used for an old seat. V8 must not overwrite it.
        jdbc.update("UPDATE seats SET seat_code='A17' WHERE vehicle_id=? AND position_number=16", bus);
        var original = jdbc.queryForList("SELECT * FROM seats ORDER BY id");
        String oldVehicleCheck = constraint(jdbc, "chk_vehicle_seat_layout");
        String oldSeatCheck = constraint(jdbc, "chk_seat_position");
        assertThatThrownBy(() -> flyway(source, "8").migrate()).isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("uq_vehicle_seat");
        assertThat(jdbc.queryForList("SELECT * FROM seats ORDER BY id")).isEqualTo(original);
        assertThat(constraint(jdbc, "chk_vehicle_seat_layout")).isEqualTo(oldVehicleCheck);
        assertThat(constraint(jdbc, "chk_seat_position")).isEqualTo(oldSeatCheck);
        assertThat(flyway(source, "7").info().current().getVersion().toString()).isEqualTo("7");
    }

    @Test
    void v9InjectedFailureRestoresDroppedChecksAndConvertedSeats() {
        DataSource source = source("bad_v9_injected");
        var jdbc = new JdbcTemplate(source);
        flyway(source, "8").migrate();
        long bus = vehicle(jdbc, "BUS_34", "BUS");
        busSeats(jdbc, bus, 34);
        // Fail after seat conversion, when V9 updates the vehicle. Test-only fault injection.
        jdbc.execute("CREATE FUNCTION audit_fail_conversion() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Injected conversion failure'; END $$");
        jdbc.execute("CREATE TRIGGER audit_fail_conversion BEFORE UPDATE ON vehicles FOR EACH ROW EXECUTE FUNCTION audit_fail_conversion()");
        var original = jdbc.queryForList("SELECT * FROM seats ORDER BY id");
        String oldVehicleCheck = constraint(jdbc, "chk_vehicle_seat_layout");
        String oldSeatCheck = constraint(jdbc, "chk_seat_position");
        assertThatThrownBy(() -> flyway(source, "9").migrate()).isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("Injected conversion failure");
        assertThat(jdbc.queryForList("SELECT * FROM seats ORDER BY id")).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT seat_layout FROM vehicles WHERE id=?", String.class, bus)).isEqualTo("BUS_34");
        assertThat(constraint(jdbc, "chk_vehicle_seat_layout")).isEqualTo(oldVehicleCheck);
        assertThat(constraint(jdbc, "chk_seat_position")).isEqualTo(oldSeatCheck);
        assertThat(flyway(source, "8").info().current().getVersion().toString()).isEqualTo("8");
    }

    @Test
    void duplicatePendingPaymentsInV6RollBackDdlAndKeepExistingRows() {
        DataSource source = source("bad_v6");
        var jdbc = new JdbcTemplate(source);
        flyway(source, "5").migrate();
        long bus = id(jdbc, "INSERT INTO vehicles(vehicle_code,vehicle_type,display_name) VALUES ('audit-bus','BUS','Fixture') RETURNING id");
        long booking = booking(jdbc, bus);
        jdbc.update("INSERT INTO payments(booking_id,txn_ref,amount) VALUES (?,'pending-a',100000),(?,'pending-b',100000)", booking, booking);
        var original = jdbc.queryForList("SELECT * FROM payments ORDER BY id");
        assertThatThrownBy(() -> flyway(source, "6").migrate()).isInstanceOf(FlywayException.class);
        assertThat(jdbc.queryForList("SELECT * FROM payments ORDER BY id")).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='payments' AND column_name='expires_at'", Long.class)).isZero();
        assertThat(flyway(source, "5").info().current().getVersion().toString()).isEqualTo("5");
    }

    private long vehicle(JdbcTemplate jdbc, String layout, String type) {
        return id(jdbc, "INSERT INTO vehicles(vehicle_code,vehicle_type,display_name,seat_layout) VALUES (?,?, 'Fixture',?) RETURNING id", type + "-fixture", type, layout);
    }

    private void busSeats(JdbcTemplate jdbc, long bus, int count) {
        jdbc.update("INSERT INTO seats(vehicle_id,seat_code,seat_class,coach_number,deck_number,position_number) SELECT ?, 'A'||n, 'STANDARD',0,1,n FROM generate_series(1,?) n", bus, count);
    }

    private long booking(JdbcTemplate jdbc, long vehicle) {
        long user = id(jdbc, "INSERT INTO users(full_name,email,password_hash) VALUES ('Fixture','audit@example.invalid','not-a-real-password') RETURNING id");
        long route = id(jdbc, "INSERT INTO routes(origin,destination) VALUES ('A','B') RETURNING id");
        long trip = id(jdbc, "INSERT INTO trips(route_id,vehicle_id,departure_at,arrival_at,base_price) VALUES (?,?,CURRENT_TIMESTAMP+INTERVAL '1 day',CURRENT_TIMESTAMP+INTERVAL '1 day 2 hours',100000) RETURNING id", route, vehicle);
        return id(jdbc, "INSERT INTO bookings(booking_code,user_id,trip_id,vehicle_id,status,hold_expires_at,total_amount) VALUES ('audit-booking',?,?,?,'PAID',CURRENT_TIMESTAMP+INTERVAL '10 minutes',100000) RETURNING id", user, trip, vehicle);
    }

    private long id(JdbcTemplate jdbc, String sql, Object... parameters) {
        return jdbc.queryForObject(sql, Long.class, parameters);
    }

    private String constraint(JdbcTemplate jdbc, String name) {
        return jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE connamespace='public'::regnamespace AND conname=?", String.class, name);
    }
}
