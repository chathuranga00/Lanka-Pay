package com.lankapay.goals.domain;

import java.time.Instant;

/**
 * Immutable append-only ledger entry tracking contributions and withdrawals
 * to a simulated savings goal.
 */
public record GoalLedgerEntry(
        String id,
        String goalId,
        Long userId,
        GoalLedgerEntryType type,
        long amountMinor,
        long balanceAfterMinor,
        String idempotencyKey,
        String note,
        Instant timestamp,
        String ledgerType
) {
    public static final String SIMULATED_LEDGER_LABEL = "SIMULATED";

    public GoalLedgerEntry {
        if (ledgerType == null) {
            ledgerType = SIMULATED_LEDGER_LABEL;
        }
    }
}
