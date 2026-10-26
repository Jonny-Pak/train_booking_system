package com.example.ticketbooking.ticket;

import java.math.BigDecimal;
import java.time.Instant;

public record TicketResponse(String ticketCode, long bookingId, String bookingCode, String passengerName,
        String origin, String destination, String vehicleName, String vehicleCode, String vehicleType,
        Instant departureAt, Instant arrivalAt, String seatCode, String seatClass,
        BigDecimal unitPrice, Instant issuedAt, String qrContent, Integer coachNumber,
        Integer compartmentNumber, Integer deckNumber, Integer positionNumber) { }
