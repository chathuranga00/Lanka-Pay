package com.lankapay.analytics.web.dto;

import com.lankapay.analytics.domain.Budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public record BudgetResponse(
        String id,
        Long userId,
        String month,
        Long monthlyTotalMinor,
        BigDecimal monthlyTotalAmount,
        Long spentMinor,
        BigDecimal spentAmount,
        double usedPercentage,
        Long remainingMinor,
        BigDecimal remainingAmount,
        Map<String, Long> categoryBudgetsMinor,
        boolean notified80,
        boolean notified100
) {
    public static BudgetResponse from(Budget budget, Long spentMinor) {
        long spent = spentMinor != null ? spentMinor : 0L;
        long total = budget.getMonthlyTotalMinor() != null ? budget.getMonthlyTotalMinor() : 0L;
        double pct = total > 0 ? Math.min(999.0, (spent * 100.0) / total) : 0.0;
        long remaining = Math.max(0L, total - spent);

        BigDecimal totalAmt = BigDecimal.valueOf(total, 2);
        BigDecimal spentAmt = BigDecimal.valueOf(spent, 2);
        BigDecimal remAmt = BigDecimal.valueOf(remaining, 2);

        return new BudgetResponse(
                budget.getId(),
                budget.getUserId(),
                budget.getMonth(),
                total,
                totalAmt,
                spent,
                spentAmt,
                Math.round(pct * 100.0) / 100.0,
                remaining,
                remAmt,
                budget.getCategoryBudgetsMinor(),
                budget.isNotified80(),
                budget.isNotified100()
        );
    }
}
