package com.example.ticketbooking.booking;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bookings")
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_code", nullable = false, length = 32)
    private String bookingCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "hold_expires_at", nullable = false)
    private Instant holdExpiresAt;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Booking() {
    }

    public Booking(String bookingCode, long userId, long tripId, long vehicleId,
                   BigDecimal totalAmount, Instant createdAt, Instant holdExpiresAt) {
        this.bookingCode = bookingCode;
        this.userId = userId;
        this.tripId = tripId;
        this.vehicleId = vehicleId;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
        this.holdExpiresAt = holdExpiresAt;
        this.status = BookingStatus.PENDING;
    }

    public boolean holdExpired(Instant now) {
        return status == BookingStatus.PENDING && !holdExpiresAt.isAfter(now);
    }

    public void cancel() {
        if (status != BookingStatus.PENDING) {
            throw new IllegalStateException("Only pending bookings can be cancelled");
        }
        status = BookingStatus.CANCELLED;
    }

    public void expire() {
        if (status != BookingStatus.PENDING) {
            throw new IllegalStateException("Only pending bookings can expire");
        }
        status = BookingStatus.EXPIRED;
    }

    public void pay() {
        if (status != BookingStatus.PENDING) {
            throw new IllegalStateException("Only pending bookings can be paid");
        }
        status = BookingStatus.PAID;
    }

    public Long getId() { return id; }
    public String getBookingCode() { return bookingCode; }
    public Long getUserId() { return userId; }
    public Long getTripId() { return tripId; }
    public Long getVehicleId() { return vehicleId; }
    public BookingStatus getStatus() { return status; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getCreatedAt() { return createdAt; }
}
