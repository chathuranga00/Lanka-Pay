package com.lankapay.family.web.dto;

import java.util.List;
import java.util.Set;

/**
 * Child-safe minimal view:
 * Contains only what the child needs to see their simulated balance and rules.
 * No banking details, no adult PII, no geolocation.
 */
public record ChildViewDto(
        String id,
        String firstName,
        String avatarInitials,
        long balanceMinor,
        long dailyLimitMinor,
        long remainingDailyMinor,
        Set<String> allowedCategories,
        boolean isFrozen,
        List<FamilyTransactionDto> recentTransactions
) {}
