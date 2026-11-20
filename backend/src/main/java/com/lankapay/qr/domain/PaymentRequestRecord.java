package com.lankapay.qr.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pure domain model representing a peer-to-peer / merchant payment request.
 * Contains no JPA or database-specific annotations.
 */
public record PaymentRequestRecord(
        Long id,
        Long requesterId,
        Long payerId,
        Long qrCodeId,
        String reference,
        BigDecimal amount,
        String currency,
        String note,
        PaymentRequestStatus status,
        Instant expiresAt,
        Long fulfilledTransferId,
        Instant createdAt
) {
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public PaymentRequestRecord markCompleted(Long transferId) {
        return new PaymentRequestRecord(
                id, requesterId, payerId, qrCodeId, reference, amount, currency, note,
                PaymentRequestStatus.COMPLETED, expiresAt, transferId, createdAt
        );
    }

    public PaymentRequestRecord markCancelled() {
        return new PaymentRequestRecord(
                id, requesterId, payerId, qrCodeId, reference, amount, currency, note,
                PaymentRequestStatus.CANCELLED, expiresAt, fulfilledTransferId, createdAt
        );
    }
}
