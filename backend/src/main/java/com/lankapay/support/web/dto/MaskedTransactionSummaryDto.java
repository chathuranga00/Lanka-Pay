package com.lankapay.support.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MaskedTransactionSummaryDto(
        String reference,
        BigDecimal amount,
        String currency,
        String status,
        String senderMasked,
        String receiverMasked,
        Instant createdAt,
        boolean isAttachedByCustomer
) {}
