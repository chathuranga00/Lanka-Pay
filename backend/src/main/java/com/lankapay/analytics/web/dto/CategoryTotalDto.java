package com.lankapay.analytics.web.dto;

import java.math.BigDecimal;

public record CategoryTotalDto(
        String category,
        String displayName,
        String colorHex,
        Long totalMinor,
        BigDecimal totalAmount,
        int count,
        double percentage
) {}
