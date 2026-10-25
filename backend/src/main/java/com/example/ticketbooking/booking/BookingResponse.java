package com.example.ticketbooking.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingResponse(
        Long id, String bookingCode, Long userId, Long tripId, Long vehicleId,
        BookingStatus status, BigDecimal totalAmount, Instant holdExpiresAt, Instant createdAt,
        boolean holdExpired, List<BookingSeatResponse> seats) {
}
