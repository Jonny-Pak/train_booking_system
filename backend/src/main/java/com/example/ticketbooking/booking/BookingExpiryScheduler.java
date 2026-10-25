package com.example.ticketbooking.booking;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "booking.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class BookingExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(BookingExpiryScheduler.class);
    private final JdbcTemplate jdbc;
    private final BookingExpiryService expiry;
    private final Clock clock;

    public BookingExpiryScheduler(JdbcTemplate jdbc, BookingExpiryService expiry, Clock clock) {
        this.jdbc = jdbc;
        this.expiry = expiry;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${booking.expiry.poll-milliseconds:30000}",
               initialDelayString = "${booking.expiry.poll-milliseconds:30000}")
    public void releaseExpiredHolds() {
        List<Long> tripIds = jdbc.queryForList("""
                SELECT DISTINCT trip_id FROM bookings
                WHERE status = 'PENDING' AND hold_expires_at <= ?
                ORDER BY trip_id LIMIT 100
                """, Long.class, clock.instant().atOffset(ZoneOffset.UTC));

        for (Long tripId : tripIds) {
            try {
                // Mỗi chuyến một transaction. Lỗi một chuyến không rollback các chuyến đã xong.
                expiry.expireTrip(tripId);
            } catch (RuntimeException exception) {
                log.warn("Booking expiry failed for trip {}; will retry. Error type: {}",
                        tripId, exception.getClass().getSimpleName());
            }
        }
    }
}
