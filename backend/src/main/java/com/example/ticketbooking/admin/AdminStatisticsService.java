package com.example.ticketbooking.admin;

import com.example.ticketbooking.common.ApiException;
import java.sql.Timestamp;
import java.time.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminStatisticsService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AdminStatisticsService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminStatistics get(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        if (from == null) from = today.withDayOfMonth(1);
        if (to == null) to = today;
        if (to.isBefore(from) || from.isBefore(LocalDate.of(2000, 1, 1))
                || to.isAfter(LocalDate.of(2100, 12, 31)) || to.toEpochDay() - from.toEpochDay() > 365) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "Choose a valid range of at most 366 days");
        }
        Timestamp start = Timestamp.from(from.atStartOfDay(ZONE).toInstant());
        Timestamp end = Timestamp.from(to.plusDays(1).atStartOfDay(ZONE).toInstant());
        Timestamp now = Timestamp.from(clock.instant());
        // Booking counts use creation date. An overdue hold is expired even before the scheduler runs.
        long[] counts = jdbc.queryForObject("""
                select count(*), count(*) filter (where status='PAID'),
                  count(*) filter (where status='PENDING' and hold_expires_at > ?),
                  count(*) filter (where status='EXPIRED' or (status='PENDING' and hold_expires_at <= ?)),
                  count(*) filter (where status='CANCELLED')
                from bookings where created_at >= ? and created_at < ?
                """, (rs, row) -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)},
                now, now, start, end);
        // Revenue uses payment date and excludes money requiring manual reconciliation.
        var revenue = jdbc.queryForObject("""
                select coalesce(sum(p.amount),0) from payments p join bookings b on b.id=p.booking_id
                where p.status='SUCCESS' and not p.reconciliation_required and b.status='PAID'
                  and p.paid_at >= ? and p.paid_at < ?
                """, java.math.BigDecimal.class, start, end);
        long tickets = jdbc.queryForObject("select count(*) from tickets where issued_at >= ? and issued_at < ?",
                Long.class, start, end);
        var review = jdbc.queryForMap("""
                select count(*) as total, coalesce(sum(amount),0) as amount from payments
                where reconciliation_required and created_at >= ? and created_at < ?
                """, start, end);
        return new AdminStatistics(from, to, counts[0], counts[1], counts[2], counts[3], counts[4], revenue,
                tickets, ((Number) review.get("total")).longValue(), (java.math.BigDecimal) review.get("amount"));
    }
}
