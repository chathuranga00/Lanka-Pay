package com.lankapay.admin.web.dto;

import com.lankapay.admin.domain.FraudAlert;
import com.lankapay.admin.domain.FraudAlertSeverity;
import com.lankapay.admin.domain.FraudAlertStatus;
import com.lankapay.admin.domain.FraudAlertType;
import com.lankapay.common.util.PhoneNumberNormalizer;

import java.time.Instant;

public record FraudAlertResponse(
        Long id,
        Long userId,
        String userMaskedPhone,
        Long transferId,
        String transferReference,
        FraudAlertType alertType,
        FraudAlertSeverity severity,
        FraudAlertStatus status,
        String description,
        String metadataJson,
        Instant createdAt,
        Instant resolvedAt,
        Long resolvedBy,
        String resolutionNotes
) {
    public static FraudAlertResponse from(com.lankapay.admin.domain.FraudAlertRecord alert) {
        return new FraudAlertResponse(
                alert.id(),
                alert.userId(),
                PhoneNumberNormalizer.mask(alert.userPhone()),
                alert.transferId(),
                alert.transferReference(),
                alert.alertType(),
                alert.severity(),
                alert.status(),
                alert.description(),
                alert.metadataJson(),
                alert.createdAt(),
                alert.resolvedAt(),
                alert.resolvedBy(),
                alert.resolutionNotes()
        );
    }

    public static FraudAlertResponse from(FraudAlert alert) {
        return new FraudAlertResponse(
                alert.getId(),
                alert.getUser().getId(),
                PhoneNumberNormalizer.mask(alert.getUser().getPhone()),
                alert.getTransfer() != null ? alert.getTransfer().getId() : null,
                alert.getTransfer() != null ? alert.getTransfer().getReference() : null,
                alert.getAlertType(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getDescription(),
                alert.getMetadataJson(),
                alert.getCreatedAt(),
                alert.getResolvedAt(),
                alert.getResolvedBy(),
                alert.getResolutionNotes()
        );
    }
}
