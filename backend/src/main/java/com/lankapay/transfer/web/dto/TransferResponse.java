package com.lankapay.transfer.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lankapay.card.domain.CardKind;
import com.lankapay.transfer.domain.FundingType;
import com.lankapay.transfer.domain.TransactionRecord;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.Transfer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Response representation of a transfer")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransferResponse(

        @Schema(description = "Internal transfer ID", example = "99")
        Long transferId,

        @Schema(description = "Public UUID reference", example = "550e8400-e29b-41d4-a716-446655440000")
        String reference,

        @Schema(description = "Current lifecycle status per state machine", example = "COMPLETED")
        TransactionStatus status,

        @Schema(description = "Transfer amount in LKR", example = "1500.00")
        BigDecimal amount,

        @Schema(description = "Currency", example = "LKR")
        String currency,

        @Schema(description = "Sender user ID", example = "1")
        Long senderId,

        @Schema(description = "Sender masked phone", example = "+9477***1234")
        String senderPhone,

        @Schema(description = "Receiver user ID", example = "42")
        Long receiverId,

        @Schema(description = "Receiver display name", example = "Kamal P.")
        String receiverDisplayName,

        @Schema(description = "Bank transaction reference (from adapter)", example = "SIM-abc123")
        String bankTxRef,

        @Schema(description = "Provider reference for reconciliation", example = "SIM-A-1700000000000-1234")
        String providerReference,

        @Schema(description = "Optional note", example = "Lunch split")
        String note,

        @Schema(description = "Failure reason if status is FAILED", example = "null")
        String failureReason,

        @Schema(description = "Always true in prototype", example = "true")
        boolean simulated,

        @Schema(description = "Creation timestamp")
        Instant createdAt,

        @Schema(description = "Last update timestamp")
        Instant updatedAt,

        @Schema(description = "Funding source type", example = "CARD")
        FundingType fundingType,

        @Schema(description = "Card ID if card-funded", example = "10")
        Long cardId,

        @Schema(description = "Card last 4 digits if card-funded", example = "0010")
        String cardLast4,

        @Schema(description = "Card kind", example = "LINKED")
        CardKind cardKind,

        @Schema(description = "Card processing fee", example = "22.50")
        BigDecimal fee,

        @Schema(description = "Total debited amount", example = "1522.50")
        BigDecimal totalAmount,

        @Schema(description = "Friendly payment method display", example = "Paid with card •••• 0010 (TEST CARD)")
        String paymentMethod,

        @Schema(description = "Operation result summary", example = "COMPLETED")
        String result
) {

    /**
     * Backward-compatible 16-arg constructor.
     */
    public TransferResponse(
            Long transferId,
            String reference,
            TransactionStatus status,
            BigDecimal amount,
            String currency,
            Long senderId,
            String senderPhone,
            Long receiverId,
            String receiverDisplayName,
            String bankTxRef,
            String providerReference,
            String note,
            String failureReason,
            boolean simulated,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                transferId, reference, status, amount, currency, senderId, senderPhone,
                receiverId, receiverDisplayName, bankTxRef, providerReference, note,
                failureReason, simulated, createdAt, updatedAt,
                FundingType.ACCOUNT, null, null, null, BigDecimal.ZERO, amount,
                "Paid with bank account", status != null ? status.name() : null
        );
    }

    public static TransferResponse from(TransactionRecord transfer) {
        String receiverName = transfer.receiverFullName();
        String senderPhoneMasked = transfer.senderPhone() != null ? maskPhone(transfer.senderPhone()) : null;

        FundingType ft = transfer.fundingType() != null ? transfer.fundingType() : FundingType.ACCOUNT;
        String paymentMethod;
        if (ft == FundingType.CARD) {
            String last4 = transfer.cardLast4() != null ? transfer.cardLast4() : "••••";
            paymentMethod = "Paid with card •••• " + last4 + " (TEST CARD)";
        } else {
            paymentMethod = "Paid with bank account";
        }

        String res = transfer.status() != null ? transfer.status().name() : null;

        return new TransferResponse(
                transfer.id(),
                transfer.reference(),
                transfer.status(),
                transfer.amount(),
                transfer.currency(),
                transfer.senderId(),
                senderPhoneMasked,
                transfer.receiverId(),
                receiverName,
                transfer.bankTxRef(),
                transfer.providerReference(),
                transfer.note(),
                transfer.failureReason(),
                transfer.simulated(),
                transfer.createdAt(),
                transfer.updatedAt(),
                ft,
                transfer.cardId(),
                transfer.cardLast4(),
                transfer.cardKind(),
                transfer.fee() != null ? transfer.fee() : BigDecimal.ZERO,
                transfer.totalAmount() != null ? transfer.totalAmount() : transfer.amount(),
                paymentMethod,
                res
        );
    }

    public static TransferResponse from(Transfer transfer) {
        String receiverName = transfer.getReceiver() != null ? transfer.getReceiver().getFullName() : null;
        String senderPhoneMasked = transfer.getSender() != null ? maskPhone(transfer.getSender().getPhone()) : null;

        FundingType ft = transfer.getFundingType() != null ? transfer.getFundingType() : FundingType.ACCOUNT;
        String paymentMethod;
        if (ft == FundingType.CARD) {
            String last4 = transfer.getCardLast4() != null ? transfer.getCardLast4() : "••••";
            paymentMethod = "Paid with card •••• " + last4 + " (TEST CARD)";
        } else {
            paymentMethod = "Paid with bank account";
        }

        String res = transfer.getStatus() != null ? transfer.getStatus().name() : null;

        return new TransferResponse(
                transfer.getId(),
                transfer.getReference(),
                transfer.getStatus(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getSender() != null ? transfer.getSender().getId() : null,
                senderPhoneMasked,
                transfer.getReceiver() != null ? transfer.getReceiver().getId() : null,
                receiverName,
                transfer.getBankTxRef(),
                transfer.getProviderReference(),
                transfer.getNote(),
                transfer.getFailureReason(),
                transfer.isSimulated(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt(),
                ft,
                transfer.getCardId(),
                transfer.getCardLast4(),
                transfer.getCardKind(),
                transfer.getFee(),
                transfer.getTotalAmount(),
                paymentMethod,
                res
        );
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        int len = phone.length();
        return phone.substring(0, 6) + "***" + phone.substring(len - 4);
    }
}
