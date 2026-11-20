package com.lankapay.admin.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Plain Java domain record for RiskRules configuration (no JPA annotations).
 */
public record RiskRulesRecord(
        Long id,
        BigDecimal maxTransactionAmount,
        BigDecimal maxDailyAmount,
        int maxVelocityCountPerHour,
        int newAccountAgeHours,
        BigDecimal newAccountMaxTransactionAmount,
        BigDecimal newAccountMaxDailyAmount,
        int velocityWindowMinutes,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        Long updatedBy
) {
    public static RiskRulesRecord defaultRules() {
        return new RiskRulesRecord(
                1L,
                new BigDecimal("50000.00"),
                new BigDecimal("100000.00"),
                5,
                24,
                new BigDecimal("10000.00"),
                new BigDecimal("25000.00"),
                60,
                true,
                Instant.now(),
                Instant.now(),
                null
        );
    }
}
