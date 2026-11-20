package com.lankapay.analytics.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record StatementRequest(
        String month, // e.g. "2026-10"
        Instant fromDate,
        Instant toDate,
        String direction, // "ALL", "DEBIT", "CREDIT"
        String category,
        String fundingType,
        String bankCode,
        BigDecimal minAmount,
        BigDecimal maxAmount
) {}
