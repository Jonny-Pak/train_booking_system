package com.example.ticketbooking.booking;

import com.example.ticketbooking.vehicle.Seat;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "booking_seats")
public class BookingSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected BookingSeat() {
    }

    public BookingSeat(Booking booking, Seat seat, BigDecimal unitPrice) {
        this.bookingId = booking.getId();
        this.tripId = booking.getTripId();
        this.vehicleId = booking.getVehicleId();
        this.seat = seat;
        this.unitPrice = unitPrice;
        this.active = true;
    }

    public void release() {
        active = false;
    }

    public Long getId() { return id; }
    public Seat getSeat() { return seat; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public boolean isActive() { return active; }
}
