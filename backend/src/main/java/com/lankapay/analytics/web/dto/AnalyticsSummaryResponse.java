package com.lankapay.analytics.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record AnalyticsSummaryResponse(
        String month,
        Long spentMinor,
        BigDecimal spentAmount,
        Long receivedMinor,
        BigDecimal receivedAmount,
        Long pendingSpentMinor,
        BigDecimal pendingSpentAmount,
        int pendingCount,
        List<CategoryTotalDto> byCategoryTotals,
        List<TopPayeeDto> topPayees,
        MonthOverMonthDto monthOverMonth,
        Long budgetTotalMinor,
        BigDecimal budgetTotalAmount,
        Double budgetUsedPercentage,
        Long budgetRemainingMinor,
        BigDecimal budgetRemainingAmount
) {}
