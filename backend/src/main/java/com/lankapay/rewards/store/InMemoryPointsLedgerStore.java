package com.lankapay.rewards.store;

import com.lankapay.rewards.domain.PointsEntryType;
import com.lankapay.rewards.domain.PointsLedgerEntry;
import com.lankapay.rewards.domain.PointsLedgerStore;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe In-Memory implementation of PointsLedgerStore.
 * Enforces append-only semantics and atomic running balance computation per user.
 */
public class InMemoryPointsLedgerStore implements PointsLedgerStore {

    // User ID -> list of ledger entries (ordered oldest to newest)
    private final Map<Long, List<PointsLedgerEntry>> userLedgers = new ConcurrentHashMap<>();

    // Lock per user to ensure atomic balance checking and append
    private final Map<Long, Object> userLocks = new ConcurrentHashMap<>();

    private Object getUserLock(Long userId) {
        return userLocks.computeIfAbsent(userId, k -> new Object());
    }

    @Override
    public PointsLedgerEntry append(
            Long userId,
            PointsEntryType type,
            int pointsChange,
            String sourceTransactionId,
            String reason,
            String offerId,
            Instant expiresAt,
            Map<String, String> metadata
    ) {
        synchronized (getUserLock(userId)) {
            List<PointsLedgerEntry> entries = userLedgers.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
            int previousBalance = entries.isEmpty() ? 0 : entries.get(entries.size() - 1).runningBalance();
            int newBalance = previousBalance + pointsChange;

            PointsLedgerEntry entry = new PointsLedgerEntry(
                    UUID.randomUUID().toString(),
                    userId,
                    type,
                    pointsChange,
                    newBalance,
                    sourceTransactionId,
                    reason,
                    offerId,
                    expiresAt,
                    Instant.now(),
                    metadata != null ? metadata : Collections.emptyMap()
            );

            entries.add(entry);
            return entry;
        }
    }

    @Override
    public List<PointsLedgerEntry> findByUserId(Long userId) {
        List<PointsLedgerEntry> entries = userLedgers.get(userId);
        if (entries == null) {
            return Collections.emptyList();
        }
        // Return defensive copy
        return new ArrayList<>(entries);
    }

    @Override
    public boolean hasEarnedForTransaction(Long userId, String sourceTransactionId) {
        if (sourceTransactionId == null) return false;
        List<PointsLedgerEntry> entries = userLedgers.get(userId);
        if (entries == null) return false;

        return entries.stream().anyMatch(e ->
                e.type() == PointsEntryType.EARN && sourceTransactionId.equals(e.sourceTransactionId()));
    }

    @Override
    public List<PointsLedgerEntry> findEarnEntriesForTransaction(Long userId, String sourceTransactionId) {
        if (sourceTransactionId == null) return Collections.emptyList();
        List<PointsLedgerEntry> entries = userLedgers.get(userId);
        if (entries == null) return Collections.emptyList();

        List<PointsLedgerEntry> result = new ArrayList<>();
        for (PointsLedgerEntry e : entries) {
            if (e.type() == PointsEntryType.EARN && sourceTransactionId.equals(e.sourceTransactionId())) {
                result.add(e);
            }
        }
        return result;
    }

    @Override
    public int getCurrentBalance(Long userId) {
        List<PointsLedgerEntry> entries = userLedgers.get(userId);
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        return entries.get(entries.size() - 1).runningBalance();
    }

    @Override
    public int getEarnedPointsToday(Long userId, Instant startOfDay) {
        List<PointsLedgerEntry> entries = userLedgers.get(userId);
        if (entries == null) return 0;

        return entries.stream()
                .filter(e -> e.type() == PointsEntryType.EARN && !e.createdAt().isBefore(startOfDay))
                .mapToInt(PointsLedgerEntry::points)
                .sum();
    }

    @Override
    public int getEarnedPointsThisMonth(Long userId, Instant startOfMonth) {
        List<PointsLedgerEntry> entries = userLedgers.get(userId);
        if (entries == null) return 0;

        return entries.stream()
                .filter(e -> e.type() == PointsEntryType.EARN && !e.createdAt().isBefore(startOfMonth))
                .mapToInt(PointsLedgerEntry::points)
                .sum();
    }

    @Override
    public int getConfirmedTransactionCountThisMonth(Long userId, Instant startOfMonth) {
        List<PointsLedgerEntry> entries = userLedgers.get(userId);
        if (entries == null) return 0;

        // Distinct transactions that resulted in earning points this month
        return (int) entries.stream()
                .filter(e -> e.type() == PointsEntryType.EARN && !e.createdAt().isBefore(startOfMonth))
                .map(PointsLedgerEntry::sourceTransactionId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .count();
    }

    @Override
    public int getExpiringSoonPoints(Long userId, Instant now, Instant windowEnd) {
        List<PointsLedgerEntry> entries = userLedgers.get(userId);
        if (entries == null) return 0;

        // Points that expire between now and windowEnd and have not been expired yet
        return entries.stream()
                .filter(e -> e.type() == PointsEntryType.EARN && e.expiresAt() != null)
                .filter(e -> !e.expiresAt().isBefore(now) && !e.expiresAt().isAfter(windowEnd))
                .mapToInt(PointsLedgerEntry::points)
                .sum();
    }

    public void clear() {
        userLedgers.clear();
        userLocks.clear();
    }
}
