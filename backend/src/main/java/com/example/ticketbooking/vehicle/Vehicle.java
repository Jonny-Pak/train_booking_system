package com.example.ticketbooking.vehicle;

import jakarta.persistence.*;

@Entity
@Table(name = "vehicles")
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "vehicle_code", nullable = false, unique = true, length = 40)
    private String vehicleCode;
    @Enumerated(EnumType.STRING) @Column(name = "vehicle_type", nullable = false, length = 10)
    private VehicleType vehicleType;
    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;
    @Column(nullable = false)
    private boolean active;
    protected Vehicle() { }
    public Vehicle(VehicleRequest request) {
        update(request);
    }
    public void update(VehicleRequest request) {
        vehicleCode = request.vehicleCode();
        vehicleType = request.vehicleType();
        displayName = request.displayName();
        active = request.active();
    }
    public Long getId() { return id; }
    public String getVehicleCode() { return vehicleCode; }
    public VehicleType getVehicleType() { return vehicleType; }
    public String getDisplayName() { return displayName; }
    public boolean isActive() { return active; }
}
