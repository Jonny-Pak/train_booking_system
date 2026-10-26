package com.example.ticketbooking.auth;

import com.example.ticketbooking.user.UserResponse;
import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, long expiresIn,
                            Instant expiresAt, UserResponse user) {
    @Override
    public String toString() { return "LoginResponse[redacted]"; }
}
