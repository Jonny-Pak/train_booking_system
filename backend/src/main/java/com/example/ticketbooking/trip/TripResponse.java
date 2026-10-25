package com.example.ticketbooking.trip;

import com.example.ticketbooking.route.Route;
import com.example.ticketbooking.vehicle.Vehicle;
import com.example.ticketbooking.vehicle.VehicleType;
import java.math.BigDecimal;
import java.time.Instant;

public record TripResponse(Long id, Long routeId, String origin, String destination,
                           Long vehicleId, String vehicleCode, VehicleType vehicleType, String vehicleName,
                           Instant departureAt, Instant arrivalAt, BigDecimal basePrice, TripStatus status) {
    public static TripResponse from(Trip trip) {
        Route route = trip.getRoute();
        Vehicle vehicle = trip.getVehicle();
        return new TripResponse(trip.getId(), route.getId(), route.getOrigin(), route.getDestination(),
                vehicle.getId(), vehicle.getVehicleCode(), vehicle.getVehicleType(), vehicle.getDisplayName(),
                trip.getDepartureAt(), trip.getArrivalAt(), trip.getBasePrice(), trip.getStatus());
    }
}
