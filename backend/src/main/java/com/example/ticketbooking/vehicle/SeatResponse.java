package com.example.ticketbooking.vehicle;

public record SeatResponse(Long id, Long vehicleId, String seatCode, String seatClass,
        Integer coachNumber, Integer compartmentNumber, Integer deckNumber, Integer positionNumber) {
    public static SeatResponse from(Seat seat) {
        return new SeatResponse(seat.getId(), seat.getVehicle().getId(), seat.getSeatCode(), seat.getSeatClass(),
                seat.getCoachNumber(), seat.getCompartmentNumber(), seat.getDeckNumber(), seat.getPositionNumber());
    }
}
