package com.lankapay.rewards.web.dto;

import com.lankapay.rewards.domain.PointsEntryType;
import com.lankapay.rewards.domain.PointsLedgerEntry;

import java.time.Instant;
import java.util.Map;

public record PointsLedgerEntryDto(
        String id,
        PointsEntryType type,
        int points,
        int runningBalance,
        String sourceTransactionId,
        String reason,
        String offerId,
        Instant expiresAt,
        Instant createdAt,
        String disclaimer,
        Map<String, String> metadata
) {
    public static PointsLedgerEntryDto from(PointsLedgerEntry entry) {
        return new PointsLedgerEntryDto(
                entry.id(),
                entry.type(),
                entry.points(),
                entry.runningBalance(),
                entry.sourceTransactionId(),
                entry.reason(),
                entry.offerId(),
                entry.expiresAt(),
                entry.createdAt(),
                RewardsSummaryResponse.SIMULATED_DISCLAIMER,
                entry.metadata()
        );
    }
}
