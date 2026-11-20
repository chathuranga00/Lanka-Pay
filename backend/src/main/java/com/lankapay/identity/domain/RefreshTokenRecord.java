package com.lankapay.identity.domain;

import java.time.Instant;

/**
 * Pure domain model representing a refresh token.
 * Contains no JPA or database-specific annotations.
 */
public record RefreshTokenRecord(
        Long id,
        Long userId,
        String tokenHash,
        boolean revoked,
        Instant expiresAt,
        Instant createdAt
) {
    public RefreshTokenRecord(Long userId, String tokenHash, Instant expiresAt) {
        this(null, userId, tokenHash, false, expiresAt, Instant.now());
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }

    public RefreshTokenRecord revoke() {
        return new RefreshTokenRecord(id, userId, tokenHash, true, expiresAt, createdAt);
    }
}
