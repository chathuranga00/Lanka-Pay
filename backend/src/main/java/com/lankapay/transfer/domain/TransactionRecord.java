package com.lankapay.transfer.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Plain Java domain record for a transfer transaction (no JPA annotations).
 */
public record TransactionRecord(
        Long id,
        String reference,
        String idempotencyKey,
        Long senderId,
        String senderPhone,
        String senderFullName,
        Long receiverId,
        String receiverPhone,
        String receiverFullName,
        String senderBankCode,
        String receiverBankCode,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        String failureReason,
        String bankTxRef,
        String providerReference,
        String note,
        boolean simulated,
        TransferInitiatedVia initiatedVia,
        Long qrCodeId,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
