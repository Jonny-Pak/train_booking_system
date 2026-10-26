package com.example.ticketbooking.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Email @Size(max = 190) String email,
                           @NotBlank @ValidPassword String password) {
    public LoginRequest { email = EmailNormalizer.normalize(email); }

    @Override
    public String toString() { return "LoginRequest[redacted]"; }
}
