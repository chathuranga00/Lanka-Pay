package com.lankapay.split.web.dto;

import com.lankapay.split.domain.ParticipantStatus;
import com.lankapay.split.domain.SplitParticipant;

import java.time.Duration;
import java.time.Instant;

public record SplitParticipantResponse(
        Long userId,
        String phone,
        String displayName,
        Long shareMinor,
        Double shareAmount,
        ParticipantStatus status,
        Long paymentRequestId,
        String paymentRequestReference,
        String qrPayload,
        Long transferId,
        Instant paidAt,
        Instant lastRemindedAt,
        boolean canRemind
) {
    public static SplitParticipantResponse from(SplitParticipant p) {
        boolean canRemind = p.getStatus() == ParticipantStatus.INVITED &&
                (p.getLastRemindedAt() == null || Duration.between(p.getLastRemindedAt(), Instant.now()).toHours() >= 24);

        return new SplitParticipantResponse(
                p.getUserId(),
                p.getPhone(),
                p.getDisplayName(),
                p.getShareMinor(),
                p.getShareMinor() != null ? p.getShareMinor() / 100.0 : null,
                p.getStatus(),
                p.getPaymentRequestId(),
                p.getPaymentRequestReference(),
                p.getQrPayload(),
                p.getTransferId(),
                p.getPaidAt(),
                p.getLastRemindedAt(),
                canRemind
        );
    }
}
