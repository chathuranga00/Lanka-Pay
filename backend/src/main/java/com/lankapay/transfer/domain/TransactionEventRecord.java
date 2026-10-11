package com.lankapay.transfer.domain;

import java.time.Instant;

/**
 * Plain Java domain record for transaction state machine events (no JPA annotations).
 */
public record TransactionEventRecord(
        Long id,
        Long transferId,
        TransactionStatus fromStatus,
        TransactionStatus toStatus,
        String reason,
        String detailJson,
        Instant occurredAt
) {
}
