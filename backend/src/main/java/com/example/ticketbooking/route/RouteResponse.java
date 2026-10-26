package com.example.ticketbooking.route;

public record RouteResponse(Long id, String origin, String destination, boolean active) {
    public static RouteResponse from(Route route) {
        return new RouteResponse(route.getId(), route.getOrigin(), route.getDestination(), route.isActive());
    }
}
