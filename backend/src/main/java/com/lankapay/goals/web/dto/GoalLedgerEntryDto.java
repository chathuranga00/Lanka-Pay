package com.lankapay.goals.web.dto;

import com.lankapay.goals.domain.GoalLedgerEntry;
import com.lankapay.goals.domain.GoalLedgerEntryType;

import java.time.Instant;

public record GoalLedgerEntryDto(
        String id,
        String goalId,
        GoalLedgerEntryType type,
        long amountMinor,
        long balanceAfterMinor,
        String idempotencyKey,
        String note,
        Instant timestamp,
        String ledgerType
) {
    public static GoalLedgerEntryDto fromDomain(GoalLedgerEntry entry) {
        return new GoalLedgerEntryDto(
                entry.id(),
                entry.goalId(),
                entry.type(),
                entry.amountMinor(),
                entry.balanceAfterMinor(),
                entry.idempotencyKey(),
                entry.note(),
                entry.timestamp(),
                entry.ledgerType()
        );
    }
}
