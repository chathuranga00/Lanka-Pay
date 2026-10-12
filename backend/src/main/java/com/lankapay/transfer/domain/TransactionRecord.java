package com.lankapay.transfer.domain;

import com.lankapay.card.domain.CardKind;
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
        Instant updatedAt,
        FundingType fundingType,
        Long cardId,
        String cardLast4,
        CardKind cardKind,
        BigDecimal fee,
        BigDecimal totalAmount,
        String challengeRef,
        Integer challengeAttempts
) {

    /**
     * Backward-compatible 24-arg constructor defaulting to ACCOUNT funding.
     */
    public TransactionRecord(
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
        this(
                id, reference, idempotencyKey, senderId, senderPhone, senderFullName,
                receiverId, receiverPhone, receiverFullName, senderBankCode, receiverBankCode,
                amount, currency, status, failureReason, bankTxRef, providerReference, note,
                simulated, initiatedVia, qrCodeId, version, createdAt, updatedAt,
                FundingType.ACCOUNT, null, null, null, BigDecimal.ZERO, amount, null, 0
        );
    }
}
