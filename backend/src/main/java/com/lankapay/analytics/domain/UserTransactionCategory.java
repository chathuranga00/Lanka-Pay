package com.lankapay.analytics.domain;

import java.time.Instant;

/**
 * Record representing a user's custom recategorisation or recolouring for a transaction.
 */
public record UserTransactionCategory(
        Long userId,
        Long transactionId,
        String transactionType, // "TRANSFER" or "BILL"
        TransactionCategory category,
        String colorHex,
        Instant updatedAt
) {
    public static String buildKey(Long userId, String transactionType, Long transactionId) {
        return userId + ":" + (transactionType != null ? transactionType.toUpperCase() : "TRANSFER") + ":" + transactionId;
    }

    public String getKey() {
        return buildKey(userId, transactionType, transactionId);
    }
}
