package com.example.ticketbooking.trip;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record TripRequest(@NotNull @Positive Long routeId, @NotNull @Positive Long vehicleId,
                          @NotNull Instant departureAt, @NotNull Instant arrivalAt,
                          @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
                          @NotNull TripStatus status) { }
