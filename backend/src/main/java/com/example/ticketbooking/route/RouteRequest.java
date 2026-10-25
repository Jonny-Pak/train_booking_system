package com.example.ticketbooking.route;

import com.example.ticketbooking.common.CatalogGuard;
import jakarta.validation.constraints.*;

public record RouteRequest(@NotBlank @Size(max = 160) String origin,
                           @NotBlank @Size(max = 160) String destination,
                           @NotNull Boolean active) {
    public RouteRequest {
        origin = CatalogGuard.text(origin);
        destination = CatalogGuard.text(destination);
    }
}
