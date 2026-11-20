package com.lankapay.identity.domain;

import java.time.Instant;

/**
 * Pure domain model representing an OTP record.
 * Contains no JPA or database-specific annotations.
 */
public record OtpRecord(
        Long id,
        Long userId,
        String otpHash,
        OtpPurpose purpose,
        boolean used,
        Instant expiresAt,
        Instant createdAt
) {
    public OtpRecord(Long userId, String otpHash, OtpPurpose purpose, Instant expiresAt) {
        this(null, userId, otpHash, purpose, false, expiresAt, Instant.now());
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public OtpRecord markUsed() {
        return new OtpRecord(id, userId, otpHash, purpose, true, expiresAt, createdAt);
    }
}
