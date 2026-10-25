package com.example.ticketbooking.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CatalogGuard {
    private final JdbcTemplate jdbc;
    public CatalogGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Admin writes are infrequent. One transaction-scoped PostgreSQL lock gives all catalog
    // services the same serialization order, including schedule overlap checks across instances.
    public void lockWrites() {
        jdbc.execute("SELECT pg_advisory_xact_lock(713003)");
    }

    public void lockTrip(long id) {
        jdbc.queryForList("SELECT id FROM trips WHERE id = ? FOR UPDATE", Long.class, id);
    }

    public void lockVehicleTrips(long id) {
        jdbc.queryForList("SELECT id FROM trips WHERE vehicle_id = ? ORDER BY id FOR UPDATE", Long.class, id);
    }

    public void lockRouteTrips(long id) {
        jdbc.queryForList("SELECT id FROM trips WHERE route_id = ? ORDER BY id FOR UPDATE", Long.class, id);
    }

    public boolean tripHasBookings(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM bookings WHERE trip_id = ?)", Boolean.class, id));
    }

    public boolean vehicleHasBookings(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM bookings b JOIN trips t ON t.id = b.trip_id WHERE t.vehicle_id = ?)
                """, Boolean.class, id));
    }

    public static PageRequest page(int page, int size, String... order) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "page must be >= 0 and size must be 1..100");
        }
        return PageRequest.of(page, size, Sort.by(order));
    }

    public static void positiveId(long id) {
        if (id <= 0) {
            throw badRequest("INVALID_ID", "ID must be positive");
        }
    }

    public static ApiException notFound(String resource) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " not found");
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static String text(String value) {
        if (value == null) {
            return null;
        }
        return value.strip();
    }
}
