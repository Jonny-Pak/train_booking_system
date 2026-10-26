package com.example.ticketbooking.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerificationRequest(@NotBlank @Pattern(regexp = "[a-f0-9]{64}") String token) {
    @Override
    public String toString() { return "VerificationRequest[redacted]"; }
}
