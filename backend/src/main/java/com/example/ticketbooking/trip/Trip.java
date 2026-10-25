package com.example.ticketbooking.trip;

import com.example.ticketbooking.route.Route;
import com.example.ticketbooking.vehicle.Vehicle;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trips")
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "route_id", nullable = false)
    private Route route;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;
    @Column(name = "departure_at", nullable = false)
    private Instant departureAt;
    @Column(name = "arrival_at", nullable = false)
    private Instant arrivalAt;
    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private TripStatus status;
    protected Trip() { }
    public Trip(Route route, Vehicle vehicle, TripRequest request) {
        update(route, vehicle, request);
    }
    public void update(Route route, Vehicle vehicle, TripRequest request) {
        this.route = route;
        this.vehicle = vehicle;
        departureAt = request.departureAt();
        arrivalAt = request.arrivalAt();
        basePrice = request.basePrice();
        status = request.status();
    }
    public Long getId() { return id; }
    public Route getRoute() { return route; }
    public Vehicle getVehicle() { return vehicle; }
    public Instant getDepartureAt() { return departureAt; }
    public Instant getArrivalAt() { return arrivalAt; }
    public BigDecimal getBasePrice() { return basePrice; }
    public TripStatus getStatus() { return status; }
}
