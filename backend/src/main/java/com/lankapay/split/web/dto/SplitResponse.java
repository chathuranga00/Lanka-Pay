package com.lankapay.split.web.dto;

import com.lankapay.split.domain.BillSplit;
import com.lankapay.split.domain.SplitStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public record SplitResponse(
        String id,
        Long ownerUserId,
        String ownerName,
        String title,
        Long totalMinor,
        Double totalAmount,
        Long ownerShareMinor,
        Double ownerShareAmount,
        String currency,
        List<SplitParticipantResponse> participants,
        int paidCount,
        int totalParticipantsCount,
        Long collectedMinor,
        Double collectedAmount,
        int progressPercent,
        Instant expiresAt,
        SplitStatus status,
        Instant createdAt,
        Instant updatedAt,
        boolean isOwner
) {
    public static SplitResponse from(BillSplit split, Long currentUserId) {
        List<SplitParticipantResponse> participantResponses = split.getParticipants() != null
                ? split.getParticipants().stream().map(SplitParticipantResponse::from).toList()
                : Collections.emptyList();

        int paid = split.getPaidCount();
        int total = split.getTotalParticipantsCount();
        int progress = total > 0 ? (int) Math.round(((double) paid / total) * 100) : 0;

        return new SplitResponse(
                split.getId(),
                split.getOwnerUserId(),
                split.getOwnerName(),
                split.getTitle(),
                split.getTotalMinor(),
                split.getTotalMinor() != null ? split.getTotalMinor() / 100.0 : null,
                split.getOwnerShareMinor(),
                split.getOwnerShareMinor() != null ? split.getOwnerShareMinor() / 100.0 : null,
                split.getCurrency(),
                participantResponses,
                paid,
                total,
                split.getCollectedMinor(),
                split.getCollectedMinor() / 100.0,
                progress,
                split.getExpiresAt(),
                split.getStatus(),
                split.getCreatedAt(),
                split.getUpdatedAt(),
                split.getOwnerUserId().equals(currentUserId)
        );
    }
}
