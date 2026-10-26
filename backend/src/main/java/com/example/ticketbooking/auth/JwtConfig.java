package com.example.ticketbooking.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(JwtSettings.class)
public class JwtConfig {
    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SecretKey jwtSigningKey(JwtSettings settings) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(settings.secret()); }
        catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET must be Base64 encoded", exception);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 32 random bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, JwtSettings settings, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator time = new JwtTimestampValidator(Duration.ZERO);
        time.setClock(clock);
        OAuth2TokenValidator<Jwt> required = jwt -> {
            if (jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                    && jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    && !jwt.getIssuedAt().isAfter(clock.instant())) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(time,
                new JwtIssuerValidator(settings.issuer()),
                new JwtClaimValidator<java.util.List<String>>("aud",
                        audience -> audience != null && audience.contains(settings.audience())), required));
        return decoder;
    }
}
