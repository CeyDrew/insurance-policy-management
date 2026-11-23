package com.portfolio.insurance.security;

import com.portfolio.insurance.security.dto.AuthResponse;
import com.portfolio.insurance.user.AppUser;
import com.portfolio.insurance.user.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Issues short-lived access tokens (signed JWT) and long-lived, rotating refresh tokens (opaque, stored hashed).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    static final String ROLES_CLAIM = "roles";
    static final String NAME_CLAIM = "name";
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMapper userMapper;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AuthResponse issueTokens(AppUser user) {
        Instant now = clock.instant();
        String accessToken = createAccessToken(user, now);

        String refreshToken = generateOpaqueToken();
        LocalDateTime createdAt = LocalDateTime.now(clock);
        refreshTokenRepository.save(new RefreshToken(user, hash(refreshToken), createdAt,
                createdAt.plus(properties.refreshTokenTtl())));

        return new AuthResponse(accessToken, refreshToken, "Bearer",
                properties.accessTokenTtl().toSeconds(), userMapper.toDto(user));
    }

    /**
     * Exchanges a valid refresh token for a new token pair. The presented token is revoked (rotation).
     * Presenting an already revoked token is treated as theft: every session of that user is revoked.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResponse refresh(String rawRefreshToken) {
        LocalDateTime now = LocalDateTime.now(clock);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not found"));
        AppUser user = token.getUser();

        if (token.isRevoked()) {
            log.warn("Revoked refresh token reused for user '{}', revoking all sessions", user.getUsername());
            refreshTokenRepository.revokeAllForUser(user.getId(), now);
            throw new InvalidRefreshTokenException("Refresh token was already used");
        }
        if (token.isExpired(now)) {
            throw new InvalidRefreshTokenException("Refresh token expired");
        }
        if (!user.isActive()) {
            token.revoke(now);
            throw new InvalidRefreshTokenException("User is disabled");
        }

        token.revoke(now);
        return issueTokens(user);
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .ifPresent(token -> token.revoke(LocalDateTime.now(clock)));
    }

    private String createAccessToken(AppUser user, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim(NAME_CLAIM, user.getFullName())
                .claim(ROLES_CLAIM, List.of(user.getRole().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
