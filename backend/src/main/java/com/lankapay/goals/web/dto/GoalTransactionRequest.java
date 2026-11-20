package com.lankapay.goals.web.dto;

import jakarta.validation.constraints.Positive;

public record GoalTransactionRequest(
        @Positive(message = "Amount must be strictly positive")
        long amountMinor,

        String note,

        String pin
) {}
