package com.example.ticketbooking.user;

import java.time.Instant;

public record UserResponse(Long id, String fullName, String email, String phone,
                           Role role, Instant createdAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getPhone(), user.getRole(), user.getCreatedAt());
    }
}
