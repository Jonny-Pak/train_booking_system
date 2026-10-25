package com.example.ticketbooking.trip;

public record TripSeatResponse(Long id, String seatCode, String seatClass, SeatAvailability status) {
    public enum SeatAvailability { AVAILABLE, HELD, BOOKED }
}
