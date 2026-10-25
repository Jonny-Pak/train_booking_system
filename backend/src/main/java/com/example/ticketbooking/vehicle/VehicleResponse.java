package com.example.ticketbooking.vehicle;

public record VehicleResponse(Long id, String vehicleCode, VehicleType vehicleType, String displayName, boolean active) {
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(vehicle.getId(), vehicle.getVehicleCode(), vehicle.getVehicleType(), vehicle.getDisplayName(), vehicle.isActive());
    }
}
