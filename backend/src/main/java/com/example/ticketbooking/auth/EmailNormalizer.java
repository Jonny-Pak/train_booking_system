package com.example.ticketbooking.auth;

import java.util.Locale;

final class EmailNormalizer {
    private EmailNormalizer() { }

    static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
