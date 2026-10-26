package com.example.ticketbooking.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

import java.time.*;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminStatisticsIntegrationTest.FixedTime.class)
@WithMockUser(roles = "ADMIN")
@Transactional
class AdminStatisticsIntegrationTest {
    private static final String URL = "/api/v1/admin/statistics";
    private static final Instant NOW = Instant.parse("2030-01-15T00:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock adminClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    @Test
    void emptyDatabaseReturnsZeroAndDefaultVietnamMonth() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.from").value("2030-01-01")).andExpect(jsonPath("$.to").value("2030-01-15"))
                .andExpect(jsonPath("$.revenue").value(0)).andExpect(jsonPath("$.totalBookings").value(0))
                .andExpect(jsonPath("$.issuedTickets").value(0));
    }

    @Test
    void onlyAdminCanReadStatistics() throws Exception {
        mvc.perform(get(URL).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get(URL).with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
    }

    @Test
    void invalidRangesAndMalformedDatesAreRejected() throws Exception {
        for (String query : new String[]{"from=2030-01-02&to=2030-01-01", "from=2030-01-01&to=2031-01-02",
                "from=bad", "from=1999-01-01", "to=9999-12-31"}) {
            mvc.perform(get(URL + "?" + query)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void countsLogicalExpiryAndExcludesLateSuccessFromRevenue() throws Exception {
        long paid = booking("PAID", NOW, NOW.plusSeconds(600));
        booking("PENDING", NOW, NOW.plusSeconds(600));
        booking("PENDING", NOW, NOW);
        booking("EXPIRED", NOW, NOW.minusSeconds(1));
        long cancelled = booking("CANCELLED", NOW, NOW);
        payment(paid, "SUCCESS", 150000, NOW, NOW, false);
        payment(cancelled, "SUCCESS", 90000, NOW, NOW, true);
        payment(paid, "FAILED", 150000, NOW, null, false);
        mvc.perform(get(URL)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBookings").value(5)).andExpect(jsonPath("$.paidBookings").value(1))
                .andExpect(jsonPath("$.pendingBookings").value(1)).andExpect(jsonPath("$.expiredBookings").value(2))
                .andExpect(jsonPath("$.cancelledBookings").value(1)).andExpect(jsonPath("$.revenue").value(150000))
                .andExpect(jsonPath("$.reconciliationPayments").value(1)).andExpect(jsonPath("$.reconciliationAmount").value(90000));
    }

    @Test
    void eachMetricUsesItsOwnVietnamDateAndEndIsExclusive() throws Exception {
        Instant start = Instant.parse("2030-01-14T17:00:00Z");
        Instant end = start.plusSeconds(86400);
        long paid = booking("PAID", start.minusSeconds(1), end);
        booking("PENDING", start, NOW.plusSeconds(600));
        booking("PENDING", end, end.plusSeconds(600));
        payment(paid, "SUCCESS", 120000, start.minusSeconds(1), start, false);
        payment(paid, "SUCCESS", 40000, start, end, false);
        Long seat = jdbc.queryForObject("insert into seats(vehicle_id,seat_code) select vehicle_id,'A1' from bookings where id=? returning id", Long.class, paid);
        Long bs = jdbc.queryForObject("""
                insert into booking_seats(booking_id,trip_id,vehicle_id,seat_id,unit_price)
                select id,trip_id,vehicle_id,?,120000 from bookings where id=? returning id
                """, Long.class, seat, paid);
        jdbc.update("insert into tickets(ticket_code,booking_seat_id,issued_at) values(?,?,?)", UUID.randomUUID().toString(), bs, Timestamp.from(start));
        mvc.perform(get(URL).param("from", "2030-01-15").param("to", "2030-01-15"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalBookings").value(1))
                .andExpect(jsonPath("$.paidBookings").value(0)).andExpect(jsonPath("$.revenue").value(120000))
                .andExpect(jsonPath("$.issuedTickets").value(1));
    }

    private long booking(String status, Instant created, Instant expires) {
        String code = UUID.randomUUID().toString();
        Long user = jdbc.queryForObject("insert into users(full_name,email,password_hash) values('Admin test',?,'unused') returning id", Long.class, code + "@example.invalid");
        Long route = jdbc.queryForObject("insert into routes(origin,destination) values('A','B') returning id", Long.class);
        Long vehicle = jdbc.queryForObject("insert into vehicles(vehicle_code,vehicle_type,display_name) values(?,'BUS','Test') returning id", Long.class, code);
        Long trip = jdbc.queryForObject("insert into trips(route_id,vehicle_id,departure_at,arrival_at,base_price) values(?,?,?, ?,1000) returning id", Long.class, route, vehicle, Timestamp.from(NOW.plusSeconds(86400)), Timestamp.from(NOW.plusSeconds(90000)));
        return jdbc.queryForObject("insert into bookings(booking_code,user_id,trip_id,vehicle_id,status,hold_expires_at,total_amount,created_at) values(?,?,?,?,?,?,1000,?) returning id", Long.class, code.substring(0, 32), user, trip, vehicle, status, Timestamp.from(expires), Timestamp.from(created));
    }

    private void payment(long booking, String status, int amount, Instant created, Instant paid, boolean review) {
        jdbc.update("insert into payments(booking_id,txn_ref,amount,status,created_at,paid_at,expires_at,reconciliation_required,reconciliation_reason) values(?,?,?,?,?,?,?,?,?)", booking, UUID.randomUUID().toString(), amount, status, Timestamp.from(created), paid == null ? null : Timestamp.from(paid), Timestamp.from(NOW.plusSeconds(600)), review, review ? "LATE_SUCCESS" : null);
    }
}
