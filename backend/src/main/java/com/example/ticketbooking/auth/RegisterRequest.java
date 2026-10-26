package com.example.ticketbooking.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email @Size(max = 190) String email,
        @NotBlank @ValidPassword String password,
        @Size(max = 20) String phone) {
    public RegisterRequest {
        fullName = fullName == null ? null : fullName.strip();
        email = EmailNormalizer.normalize(email);
        phone = phone == null ? null : phone.strip();
    }

    @Override
    public String toString() { return "RegisterRequest[redacted]"; }
}
