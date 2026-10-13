package com.lankapay.rewards.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Interface for append-only points ledger storage.
 */
public interface PointsLedgerStore {

    /**
     * Atomically appends an entry to the user's ledger, ensuring immutability.
     * Computes the new running balance from previous running balance + points.
     */
    PointsLedgerEntry append(
            Long userId,
            PointsEntryType type,
            int pointsChange,
            String sourceTransactionId,
            String reason,
            String offerId,
            Instant expiresAt,
            java.util.Map<String, String> metadata
    );

    /**
     * Retrieve all ledger entries for a user, ordered newest first or oldest first.
     */
    List<PointsLedgerEntry> findByUserId(Long userId);

    /**
     * Check if an EARN entry already exists for a specific source transaction id.
     */
    boolean hasEarnedForTransaction(Long userId, String sourceTransactionId);

    /**
     * Retrieve all EARN entries for a specific source transaction id.
     */
    List<PointsLedgerEntry> findEarnEntriesForTransaction(Long userId, String sourceTransactionId);

    /**
     * Compute current running balance for a user.
     */
    int getCurrentBalance(Long userId);

    /**
     * Sum of points earned by user today (UTC).
     */
    int getEarnedPointsToday(Long userId, Instant startOfDay);

    /**
     * Sum of points earned by user this month (UTC).
     */
    int getEarnedPointsThisMonth(Long userId, Instant startOfMonth);

    /**
     * Count confirmed transactions in the current month for tier calculation.
     */
    int getConfirmedTransactionCountThisMonth(Long userId, Instant startOfMonth);

    /**
     * Calculate points that will expire within the given window (e.g., next 30 days).
     */
    int getExpiringSoonPoints(Long userId, Instant now, Instant windowEnd);
}
