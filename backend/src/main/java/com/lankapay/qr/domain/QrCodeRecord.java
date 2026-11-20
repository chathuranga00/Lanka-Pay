package com.lankapay.qr.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pure domain model representing an opaque QR code reference.
 * Contains no JPA or database-specific annotations.
 */
public record QrCodeRecord(
        Long id,
        Long merchantId,
        String qrId,
        String reference,
        QrType qrType,
        BigDecimal amount,
        String currency,
        String note,
        QrStatus status,
        boolean oneTimeUse,
        String payload,
        Instant expiresAt,
        Instant createdAt
) {
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isUsable() {
        return status == QrStatus.ACTIVE && !isExpired();
    }

    public QrCodeRecord markUsed() {
        return new QrCodeRecord(
                id, merchantId, qrId, reference, qrType, amount, currency, note,
                QrStatus.USED, oneTimeUse, payload, expiresAt, createdAt
        );
    }

    public QrCodeRecord markExpired() {
        return new QrCodeRecord(
                id, merchantId, qrId, reference, qrType, amount, currency, note,
                QrStatus.EXPIRED, oneTimeUse, payload, expiresAt, createdAt
        );
    }
}
