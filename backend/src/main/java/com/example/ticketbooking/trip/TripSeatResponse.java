package com.example.ticketbooking.trip;

public record TripSeatResponse(Long id, String seatCode, String seatClass, SeatAvailability status,
        Integer coachNumber, Integer compartmentNumber, Integer deckNumber, Integer positionNumber) {
    public enum SeatAvailability { AVAILABLE, HELD, BOOKED }
}
