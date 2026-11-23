package com.portfolio.insurance.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Base64;

/**
 * JWT settings. The secret is a Base64-encoded HMAC key supplied through the
 * {@code JWT_SECRET} environment variable (see .env.example) and never committed.
 */
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {

    private static final int MIN_KEY_BYTES = 32; // HS256 requires at least 256 bits

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.security.jwt.secret is not set (JWT_SECRET in .env)");
        }
        if (Base64.getDecoder().decode(secret).length < MIN_KEY_BYTES) {
            throw new IllegalStateException("app.security.jwt.secret must be at least 256 bits (Base64)");
        }
    }

    public byte[] secretBytes() {
        return Base64.getDecoder().decode(secret);
    }
}
