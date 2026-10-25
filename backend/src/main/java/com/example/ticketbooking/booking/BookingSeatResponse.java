package com.example.ticketbooking.booking;

import java.math.BigDecimal;

public record BookingSeatResponse(
        Long id, Long seatId, String seatCode, String seatClass, BigDecimal unitPrice, boolean active) {
    public static BookingSeatResponse from(BookingSeat bookingSeat) {
        return new BookingSeatResponse(
                bookingSeat.getId(),
                bookingSeat.getSeat().getId(),
                bookingSeat.getSeat().getSeatCode(),
                bookingSeat.getSeat().getSeatClass(),
                bookingSeat.getUnitPrice(),
                bookingSeat.isActive());
    }
}
