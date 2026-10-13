package com.lankapay.analytics.web.dto;

import java.math.BigDecimal;

public record MonthOverMonthDto(
        Long currentMonthSpentMinor,
        BigDecimal currentMonthSpent,
        Long previousMonthSpentMinor,
        BigDecimal previousMonthSpent,
        Long differenceMinor,
        BigDecimal differenceAmount,
        double percentageChange,
        boolean increased
) {}
