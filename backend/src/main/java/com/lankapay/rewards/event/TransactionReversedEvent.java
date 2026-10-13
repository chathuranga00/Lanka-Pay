package com.lankapay.rewards.event;

import java.time.Instant;

/**
 * Event triggered when a transaction is reversed or refunded.
 */
public record TransactionReversedEvent(
        String transactionId,
        Long userId,
        String reason,
        Instant occurredAt
) {
}
