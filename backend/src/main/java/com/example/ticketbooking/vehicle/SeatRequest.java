package com.example.ticketbooking.vehicle;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record SeatRequest(@NotBlank @Size(max = 20) String seatCode, @NotBlank @Size(max = 40) String seatClass) {
    public SeatRequest {
        if (seatCode != null) {
            seatCode = seatCode.strip().toUpperCase(Locale.ROOT);
        }
        if (seatClass != null) {
            seatClass = seatClass.strip().toUpperCase(Locale.ROOT);
        }
    }
}
