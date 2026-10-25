package com.example.ticketbooking.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendVerificationRequest(@NotBlank @Email @Size(max = 190) String email) {
    public ResendVerificationRequest { email = EmailNormalizer.normalize(email); }
}
