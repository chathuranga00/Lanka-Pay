package com.lankapay.goals.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record CreateGoalRequest(
        @NotBlank(message = "Goal name is required")
        String name,

        @Positive(message = "Target amount must be positive")
        long targetMinor,

        LocalDate deadline,

        @NotBlank(message = "Source account is required")
        String sourceAccount,

        Long initialDepositMinor,

        String pin
) {}
