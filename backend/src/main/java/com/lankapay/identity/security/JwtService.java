package com.lankapay.identity.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Generates and validates JWT access tokens.
 *
 * Token claims:
 *   sub  = userId (String)
 *   role = USER | ADMIN
 *
 * Security rules:
 * - Secret is read from env var JWT_SECRET; minimum 32 chars.
 * - Access tokens expire after JWT_EXPIRY_MINUTES (default 30).
 * - No PII (phone, name, NIC) is ever put into the token.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final long expiryMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiry-minutes:30}") long expiryMinutes) {

        if (secret.length() < 32) {
            throw new IllegalArgumentException(
                    "JWT secret must be at least 32 characters. Check JWT_SECRET env var.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMinutes = expiryMinutes;
    }

    /** Generate a signed JWT for the given user. */
    public String generateToken(Long userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiryMinutes, ChronoUnit.MINUTES)))
                .signWith(signingKey)
                .compact();
    }

    /** Extract userId from a valid token. Returns null if the token is invalid. */
    public Long extractUserId(String token) {
        Claims claims = parseClaims(token);
        if (claims == null) return null;
        return Long.valueOf(claims.getSubject());
    }

    /** Extract role claim. Returns null if the token is invalid. */
    public String extractRole(String token) {
        Claims claims = parseClaims(token);
        if (claims == null) return null;
        return claims.get("role", String.class);
    }

    /** Returns true if the token signature and expiry are valid. */
    public boolean isValid(String token) {
        return parseClaims(token) != null;
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            return null;
        }
    }
}
