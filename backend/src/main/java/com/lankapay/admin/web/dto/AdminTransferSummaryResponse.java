package com.lankapay.admin.web.dto;

import com.lankapay.common.util.PhoneNumberNormalizer;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.Transfer;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminTransferSummaryResponse(
        Long transferId,
        String reference,
        Long senderId,
        String senderName,
        String senderMaskedPhone,
        Long receiverId,
        String receiverName,
        String receiverMaskedPhone,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        String senderBankCode,
        String receiverBankCode,
        String bankTxRef,
        String providerReference,
        boolean simulated,
        Instant createdAt
) {
    public static AdminTransferSummaryResponse from(Transfer transfer) {
        if (transfer == null) return null;
        return new AdminTransferSummaryResponse(
                transfer.getId(),
                transfer.getReference(),
                transfer.getSender().getId(),
                transfer.getSender().getFullName(),
                PhoneNumberNormalizer.mask(transfer.getSender().getPhone()),
                transfer.getReceiver().getId(),
                transfer.getReceiver().getFullName(),
                PhoneNumberNormalizer.mask(transfer.getReceiver().getPhone()),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getStatus(),
                transfer.getSenderBankCode(),
                transfer.getReceiverBankCode(),
                transfer.getBankTxRef(),
                transfer.getProviderReference(),
                transfer.isSimulated(),
                transfer.getCreatedAt()
        );
    }

    public static AdminTransferSummaryResponse from(com.lankapay.transfer.domain.TransactionRecord tx) {
        if (tx == null) return null;
        return new AdminTransferSummaryResponse(
                tx.id(),
                tx.reference(),
                tx.senderId(),
                tx.senderFullName(),
                PhoneNumberNormalizer.mask(tx.senderPhone()),
                tx.receiverId(),
                tx.receiverFullName(),
                PhoneNumberNormalizer.mask(tx.receiverPhone()),
                tx.amount(),
                tx.currency(),
                tx.status(),
                tx.senderBankCode(),
                tx.receiverBankCode(),
                tx.bankTxRef(),
                tx.providerReference(),
                tx.simulated(),
                tx.createdAt()
        );
    }
}

