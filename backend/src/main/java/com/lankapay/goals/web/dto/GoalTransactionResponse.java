package com.lankapay.goals.web.dto;

public record GoalTransactionResponse(
        GoalDto goal,
        GoalLedgerEntryDto entry,
        String status,
        String ledgerType,
        String message
) {
    public static GoalTransactionResponse success(GoalDto goal, GoalLedgerEntryDto entry, String message) {
        return new GoalTransactionResponse(goal, entry, "SUCCESS", "SIMULATED", message);
    }
}
