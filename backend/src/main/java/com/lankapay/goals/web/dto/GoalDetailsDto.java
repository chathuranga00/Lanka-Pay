package com.lankapay.goals.web.dto;

import java.util.List;

public record GoalDetailsDto(
        GoalDto goal,
        List<GoalLedgerEntryDto> entries,
        String disclaimer
) {
    public static final String DEFAULT_DISCLAIMER =
            "SIMULATED: internal savings ledger only. No real money is locked or moved.";

    public GoalDetailsDto(GoalDto goal, List<GoalLedgerEntryDto> entries) {
        this(goal, entries, DEFAULT_DISCLAIMER);
    }
}
