package com.example.ticketbooking.auth;

import com.example.ticketbooking.common.ApiException;
import com.example.ticketbooking.user.User;
import com.example.ticketbooking.user.UserRepository;
import com.example.ticketbooking.user.UserResponse;
import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, TokenService tokens, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.clock = clock;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (users.existsByEmail(request.email())) throw emailConflict();
        User user = User.customer(request.fullName(), request.email(), passwords.encode(request.password()),
                request.phone(), clock.instant());
        try { return UserResponse.from(users.saveAndFlush(user)); }
        catch (DataIntegrityViolationException exception) {
            Throwable cause = exception;
            while (cause != null) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                        && ("users_email_key".equals(violation.getConstraintName())
                        || "uq_users_email_normalized".equals(violation.getConstraintName()))) {
                    throw emailConflict();
                }
                cause = cause.getCause();
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        var user = users.findByEmail(request.email());
        boolean matches = passwords.matches(request.password(),
                user.map(User::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect");
        }
        return tokens.issue(user.get());
    }

    @Transactional(readOnly = true)
    public UserResponse me(long id) {
        return users.findById(id).map(UserResponse::from).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required"));
    }

    private ApiException emailConflict() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already registered");
    }
}
