package com.lankapay.admin.web.dto;

import com.lankapay.admin.domain.RiskRules;

import java.math.BigDecimal;
import java.time.Instant;

public record RiskRulesResponse(
        Long id,
        BigDecimal maxTransactionAmount,
        BigDecimal maxDailyAmount,
        int maxVelocityCountPerHour,
        int newAccountAgeHours,
        BigDecimal newAccountMaxTransactionAmount,
        BigDecimal newAccountMaxDailyAmount,
        int velocityWindowMinutes,
        boolean enabled,
        Instant updatedAt,
        Long updatedBy
) {
    public static RiskRulesResponse from(com.lankapay.admin.domain.RiskRulesRecord rules) {
        return new RiskRulesResponse(
                rules.id(),
                rules.maxTransactionAmount(),
                rules.maxDailyAmount(),
                rules.maxVelocityCountPerHour(),
                rules.newAccountAgeHours(),
                rules.newAccountMaxTransactionAmount(),
                rules.newAccountMaxDailyAmount(),
                rules.velocityWindowMinutes(),
                rules.enabled(),
                rules.updatedAt(),
                rules.updatedBy()
        );
    }

    public static RiskRulesResponse from(RiskRules rules) {
        return new RiskRulesResponse(
                rules.getId(),
                rules.getMaxTransactionAmount(),
                rules.getMaxDailyAmount(),
                rules.getMaxVelocityCountPerHour(),
                rules.getNewAccountAgeHours(),
                rules.getNewAccountMaxTransactionAmount(),
                rules.getNewAccountMaxDailyAmount(),
                rules.getVelocityWindowMinutes(),
                rules.isEnabled(),
                rules.getUpdatedAt(),
                rules.getUpdatedBy()
        );
    }
}
