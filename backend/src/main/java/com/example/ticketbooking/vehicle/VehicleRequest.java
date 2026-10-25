package com.example.ticketbooking.vehicle;

import com.example.ticketbooking.common.CatalogGuard;
import jakarta.validation.constraints.*;
import java.util.Locale;

public record VehicleRequest(@NotBlank @Size(max = 40) String vehicleCode,
                             @NotNull VehicleType vehicleType,
                             @NotBlank @Size(max = 120) String displayName,
                             @NotNull Boolean active) {
    public VehicleRequest {
        if (vehicleCode != null) {
            vehicleCode = vehicleCode.strip().toUpperCase(Locale.ROOT);
        }
        displayName = CatalogGuard.text(displayName);
    }
}
