package com.lankapay.identity.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Pure domain model representing OTP rate-limiting metadata.
 * Contains no JPA or database-specific annotations.
 */
public record OtpMetadataRecord(
        Long id,
        Long userId,
        OtpPurpose purpose,
        Instant resendCooldownUntil,
        int dailyOtpCount,
        LocalDate dailyOtpDate
) {
    public OtpMetadataRecord(Long userId, OtpPurpose purpose) {
        this(null, userId, purpose, null, 0, null);
    }

    public boolean isInCooldown() {
        return resendCooldownUntil != null && Instant.now().isBefore(resendCooldownUntil);
    }

    public OtpMetadataRecord withCooldownUntil(Instant until) {
        return new OtpMetadataRecord(id, userId, purpose, until, dailyOtpCount, dailyOtpDate);
    }

    public OtpMetadataRecord incrementDailyCount(LocalDate today) {
        int newCount = today.equals(dailyOtpDate) ? dailyOtpCount + 1 : 1;
        return new OtpMetadataRecord(id, userId, purpose, resendCooldownUntil, newCount, today);
    }
}
