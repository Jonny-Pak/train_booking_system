package com.example.ticketbooking.auth;

import com.example.ticketbooking.common.ApiException;
import com.example.ticketbooking.user.User;
import com.example.ticketbooking.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {
    private final UserRepository users;
    private final VerificationMailer mailer;
    private final Clock clock;
    private final Duration lifetime;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(UserRepository users, VerificationMailer mailer, Clock clock,
            @Value("${app.email-verification.ttl-seconds}") long ttlSeconds) {
        if (ttlSeconds < 60 || ttlSeconds > 86400) {
            throw new IllegalArgumentException("Email token lifetime must be 60..86400 seconds");
        }
        this.users = users;
        this.mailer = mailer;
        this.clock = clock;
        this.lifetime = Duration.ofSeconds(ttlSeconds);
    }

    @Transactional
    public void send(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        var now = clock.instant();
        user.setEmailVerification(hash(token), now.plus(lifetime), now);
        users.saveAndFlush(user);
        // SMTP failure rolls back registration/resend, preserving the previous token.
        mailer.send(user.getEmail(), token, now.plus(lifetime));
    }

    @Transactional
    public void verify(String token) {
        User user = users.findByVerificationHashForUpdate(hash(token)).orElseThrow(this::invalidToken);
        if (user.isEmailVerified() || !user.getVerificationExpiresAt().isAfter(clock.instant())) {
            throw invalidToken();
        }
        user.verifyEmail();
    }

    @Transactional
    public void resend(String email) {
        var found = users.findByEmailForUpdate(email);
        if (found.isEmpty() || found.get().isEmailVerified()) return;
        User user = found.get();
        if (user.getVerificationSentAt() != null
                && user.getVerificationSentAt().plusSeconds(60).isAfter(clock.instant())) return;
        send(user);
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VERIFICATION_TOKEN",
                "Verification link is invalid, expired or already used");
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
