package com.lankapay.transfer.domain;

import java.time.Instant;

/**
 * Plain Java domain record for idempotency keys (no JPA annotations).
 */
public record IdempotencyRecordModel(
        Long id,
        String idempotencyKey,
        String payloadHash,
        Long transferId,
        String responseJson,
        Integer httpStatus,
        Instant createdAt
) {
}
