package com.lankapay.goals.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Domain model for a simulated savings goal.
 * Pure internal simulated ledger only; clearly labelled SIMULATED (no real money is locked).
 */
public record GoalModel(
        String id,
        Long userId,
        String name,
        long targetMinor,
        long savedMinor,
        LocalDate deadline,
        String sourceAccount,
        GoalStatus status,
        String ledgerType,
        Instant createdAt,
        Instant updatedAt
) {
    public static final String SIMULATED_LEDGER_LABEL = "SIMULATED";

    public GoalModel {
        if (ledgerType == null) {
            ledgerType = SIMULATED_LEDGER_LABEL;
        }
    }

    public GoalModel withSavedMinor(long newSavedMinor, GoalStatus newStatus, Instant now) {
        return new GoalModel(
                id,
                userId,
                name,
                targetMinor,
                newSavedMinor,
                deadline,
                sourceAccount,
                newStatus,
                ledgerType,
                createdAt,
                now
        );
    }

    public GoalModel updateDetails(String newName, long newTargetMinor, LocalDate newDeadline, String newSourceAccount, GoalStatus newStatus, Instant now) {
        return new GoalModel(
                id,
                userId,
                newName != null ? newName : name,
                newTargetMinor > 0 ? newTargetMinor : targetMinor,
                savedMinor,
                newDeadline,
                newSourceAccount != null ? newSourceAccount : sourceAccount,
                newStatus != null ? newStatus : status,
                ledgerType,
                createdAt,
                now
        );
    }
}
