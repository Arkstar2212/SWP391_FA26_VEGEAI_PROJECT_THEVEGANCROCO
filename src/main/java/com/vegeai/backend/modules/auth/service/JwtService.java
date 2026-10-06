package com.vegeai.backend.modules.auth.service;

import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT service responsible for issuing and validating HMAC-SHA256 access tokens.
 * Access tokens are stateless (15-minute TTL); invalidation uses the blacklist.
 */
@Service
@Slf4j
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-minutes:15}")
    private long expirationMinutes;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 characters (256 bits). " +
                    "Set app.jwt.secret in environment variables.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JwtService initialized. Token TTL: {} minutes.", expirationMinutes);
    }

    // ── Token generation ─────────────────────────────────────────────────────

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationMinutes * 60);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getUserId().toString())
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    // ── Token validation / parsing ────────────────────────────────────────────

    /**
     * Validates the token and returns its claims.
     *
     * @throws AppException TOKEN_EXPIRED, TOKEN_INVALID on failure
     */
    public Claims validateAndGetClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        } catch (SignatureException | MalformedJwtException | UnsupportedJwtException ex) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        } catch (Exception ex) {
            log.warn("Unexpected JWT parse error: {}", ex.getMessage());
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
    }

    /** Extract the remaining TTL in seconds (used to set Redis/Caffeine TTL for blacklist). */
    public long getRemainingTtlSeconds(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            long expiryEpoch = claims.getExpiration().getTime();
            long remaining = (expiryEpoch - System.currentTimeMillis()) / 1000;
            return Math.max(remaining, 0);
        } catch (ExpiredJwtException ex) {
            return 0; // already expired – no need to blacklist
        }
    }

    public String extractUserId(Claims claims) {
        return claims.getSubject();
    }

    public String extractRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }
}
