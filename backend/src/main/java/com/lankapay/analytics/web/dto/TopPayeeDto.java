package com.lankapay.analytics.web.dto;

import java.math.BigDecimal;

public record TopPayeeDto(
        String name,
        Long totalMinor,
        BigDecimal totalAmount,
        int count
) {}
