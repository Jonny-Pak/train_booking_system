package com.example.ticketbooking.auth;

import com.example.ticketbooking.user.User;
import com.example.ticketbooking.user.UserResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final JwtSettings settings;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, JwtSettings settings, Clock clock) {
        this.encoder = encoder;
        this.settings = settings;
        this.clock = clock;
    }

    public LoginResponse issue(User user) {
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant expires = now.plusSeconds(settings.accessTokenSeconds());
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(settings.issuer())
                .audience(List.of(settings.audience())).subject(user.getId().toString())
                .issuedAt(now).expiresAt(expires).id(UUID.randomUUID().toString())
                .claim("role", user.getRole().name()).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", settings.accessTokenSeconds(), expires,
                UserResponse.from(user));
    }
}
