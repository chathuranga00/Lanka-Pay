package com.lankapay.bank.domain;

import java.time.Instant;

/**
 * Pure domain model representing user consent for bank account operations.
 * Contains no JPA or database-specific annotations.
 */
public record AccountConsentRecord(
        Long id,
        Long userId,
        Long bankAccountId,
        ConsentType consentType,
        boolean consentGiven,
        String consentIp,
        Instant consentedAt
) {
    public AccountConsentRecord(Long userId, Long bankAccountId, ConsentType consentType, boolean consentGiven, String consentIp) {
        this(null, userId, bankAccountId, consentType, consentGiven, consentIp, Instant.now());
    }
}
