package com.lankapay.admin.web.dto;

import com.lankapay.common.util.PhoneNumberNormalizer;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.Transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminTransferDetailResponse(
        Long transferId,
        String reference,
        String idempotencyKey,
        Long senderId,
        String senderName,
        String senderMaskedPhone,
        Long receiverId,
        String receiverName,
        String receiverMaskedPhone,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        String failureReason,
        String senderBankCode,
        String receiverBankCode,
        String bankTxRef,
        String providerReference,
        String note,
        boolean simulated,
        List<AdminTransactionEventResponse> events,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminTransferDetailResponse from(Transfer transfer, List<AdminTransactionEventResponse> events) {
        if (transfer == null) return null;
        return new AdminTransferDetailResponse(
                transfer.getId(),
                transfer.getReference(),
                transfer.getIdempotencyKey(),
                transfer.getSender().getId(),
                transfer.getSender().getFullName(),
                PhoneNumberNormalizer.mask(transfer.getSender().getPhone()),
                transfer.getReceiver().getId(),
                transfer.getReceiver().getFullName(),
                PhoneNumberNormalizer.mask(transfer.getReceiver().getPhone()),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getStatus(),
                transfer.getFailureReason(),
                transfer.getSenderBankCode(),
                transfer.getReceiverBankCode(),
                transfer.getBankTxRef(),
                transfer.getProviderReference(),
                transfer.getNote(),
                transfer.isSimulated(),
                events != null ? events : List.of(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt()
        );
    }

    public static AdminTransferDetailResponse from(com.lankapay.transfer.domain.TransactionRecord tx, List<AdminTransactionEventResponse> events) {
        if (tx == null) return null;
        return new AdminTransferDetailResponse(
                tx.id(),
                tx.reference(),
                tx.idempotencyKey(),
                tx.senderId(),
                tx.senderFullName(),
                PhoneNumberNormalizer.mask(tx.senderPhone()),
                tx.receiverId(),
                tx.receiverFullName(),
                PhoneNumberNormalizer.mask(tx.receiverPhone()),
                tx.amount(),
                tx.currency(),
                tx.status(),
                tx.failureReason(),
                tx.senderBankCode(),
                tx.receiverBankCode(),
                tx.bankTxRef(),
                tx.providerReference(),
                tx.note(),
                tx.simulated(),
                events != null ? events : List.of(),
                tx.createdAt(),
                tx.updatedAt()
        );
    }
}

