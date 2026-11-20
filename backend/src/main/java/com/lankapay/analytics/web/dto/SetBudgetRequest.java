package com.lankapay.analytics.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record SetBudgetRequest(
        @NotBlank(message = "month is required (format: YYYY-MM)")
        String month,

        @NotNull(message = "monthlyTotalMinor is required")
        @Min(value = 1, message = "monthlyTotalMinor must be greater than 0")
        Long monthlyTotalMinor,

        Map<String, Long> categoryBudgetsMinor
) {}
