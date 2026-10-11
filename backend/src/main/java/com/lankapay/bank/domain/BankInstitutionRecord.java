package com.lankapay.bank.domain;

import java.time.Instant;

/**
 * Pure domain model representing a participating bank institution.
 * Contains no JPA or database-specific annotations.
 */
public record BankInstitutionRecord(
        Long id,
        String bankCode,
        String displayName,
        boolean simulated,
        boolean active,
        Instant createdAt
) {
    public BankInstitutionRecord(String bankCode, String displayName, boolean simulated, boolean active) {
        this(null, bankCode, displayName, simulated, active, Instant.now());
    }
}
