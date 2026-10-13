package com.lankapay.city.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PayAtMerchantResponse(
        String transactionId,
        String merchantId,
        String merchantName,
        String category,
        BigDecimal amount,
        String currency,
        String qrReference,
        String status,
        Instant timestamp,
        Integer pointsEarned,
        String receiptNote
) {
}
