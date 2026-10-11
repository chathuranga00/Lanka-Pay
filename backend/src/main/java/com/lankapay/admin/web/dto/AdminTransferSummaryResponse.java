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
}
