package com.lankapay.rewards.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Immutable append-only ledger entry record.
 * Points are simulated points only and cannot be edited once appended.
 */
public record PointsLedgerEntry(
        String id,
        Long userId,
        PointsEntryType type,
        int points, // positive for EARN, ADJUST(+); negative for REDEEM, EXPIRE, REVERSE, ADJUST(-)
        int runningBalance,
        String sourceTransactionId,
        String reason,
        String offerId,
        Instant expiresAt,
        Instant createdAt,
        Map<String, String> metadata
) {
    public PointsLedgerEntry {
        if (metadata == null) {
            metadata = Collections.emptyMap();
        } else {
            metadata = Collections.unmodifiableMap(metadata);
        }
    }
}
