package com.example.ticketbooking.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtConfigTest {
    private final JwtConfig config = new JwtConfig();

    @Test
    void invalidBase64IsRejected() {
        assertThatThrownBy(() -> config.jwtSigningKey(settings("invalid-key!")))
                .isInstanceOf(IllegalStateException.class).hasMessage("JWT_SECRET must be Base64 encoded");
    }

    @Test
    void keysShorterThan256BitsAreRejected() {
        String encoded = Base64.getEncoder().encodeToString(new byte[31]);
        assertThatThrownBy(() -> config.jwtSigningKey(settings(encoded)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("at least 32");
    }

    @Test
    void settingsNeverPrintSigningSecret() {
        assertThat(settings("private-value").toString()).doesNotContain("private-value");
    }

    private JwtSettings settings(String key) { return new JwtSettings(key, "issuer", "audience", 900); }
}
