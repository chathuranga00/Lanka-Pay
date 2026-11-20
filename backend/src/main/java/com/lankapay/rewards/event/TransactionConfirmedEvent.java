package com.lankapay.rewards.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Event triggered when a financial transaction confirms SUCCESS.
 */
public record TransactionConfirmedEvent(
        String transactionId,
        String transactionType, // "TRANSFER", "BILL_PAYMENT", "QR_PAYMENT", "CARD_PAYMENT", "REFERRAL"
        Long senderUserId,
        Long receiverUserId,
        String senderBankCode,
        String receiverBankCode,
        String senderAccountNumber,
        String receiverAccountNumber,
        BigDecimal amount,
        String merchantOrCategory,
        boolean isFirstCardPayment,
        Instant occurredAt,
        Map<String, String> metadata
) {
}
