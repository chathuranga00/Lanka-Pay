package com.lankapay.analytics.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ActivityItemDto(
        Long id,
        String type, // "TRANSFER" or "BILL"
        String reference,
        String title,
        String subtitle,
        Long amountMinor,
        BigDecimal amount,
        String currency,
        String direction, // "DEBIT" or "CREDIT"
        String status,
        String category,
        String colorHex,
        String fundingType,
        String cardLast4,
        String bankCode,
        Instant createdAt
) {}
