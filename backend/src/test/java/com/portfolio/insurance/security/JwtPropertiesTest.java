package com.portfolio.insurance.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {

    private static final Duration TTL = Duration.ofMinutes(15);

    @Test
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> new JwtProperties(" ", "issuer", TTL, TTL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void rejectsSecretShorterThan256Bits() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[31]);

        assertThatThrownBy(() -> new JwtProperties(shortSecret, "issuer", TTL, TTL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    void acceptsLongEnoughSecret() {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);

        assertThat(new JwtProperties(secret, "issuer", TTL, TTL).secretBytes()).hasSize(32);
    }
}
