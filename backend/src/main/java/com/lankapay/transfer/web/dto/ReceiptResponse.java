package com.lankapay.transfer.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lankapay.card.domain.CardKind;
import com.lankapay.transfer.domain.FundingType;
import com.lankapay.transfer.domain.TransactionRecord;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.Transfer;
import com.lankapay.transfer.domain.TransferInitiatedVia;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Schema(description = "Structured transaction receipt data")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReceiptResponse(
        @Schema(description = "Unique receipt reference", example = "REC-550E8400")
        String receiptNumber,

        @Schema(description = "Internal transfer ID", example = "100")
        Long transferId,

        @Schema(description = "External transfer reference UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        String reference,

        @Schema(description = "Bank adapter provider reference or card auth code", example = "SIM-TX-9999")
        String bankTxRef,

        @Schema(description = "Transaction status (never SUCCESS unless confirmed COMPLETED)", example = "COMPLETED")
        TransactionStatus status,

        @Schema(description = "Transfer amount", example = "1500.00")
        BigDecimal amount,

        @Schema(description = "Currency", example = "LKR")
        String currency,

        @Schema(description = "Service fee", example = "0.00")
        BigDecimal fee,

        @Schema(description = "Total debited amount", example = "1500.00")
        BigDecimal totalDebited,

        @Schema(description = "Sender party details")
        ReceiptParty sender,

        @Schema(description = "Receiver party details")
        ReceiptParty receiver,

        @Schema(description = "Initiation channel", example = "APP")
        TransferInitiatedVia initiatedVia,

        @Schema(description = "Sender note", example = "Lunch split")
        String note,

        @Schema(description = "Creation timestamp")
        Instant createdAt,

        @Schema(description = "Completion / update timestamp")
        Instant completedAt,

        @Schema(description = "Simulated transaction flag", example = "true")
        boolean simulated,

        @Schema(description = "Digital verification hash for tamper detection", example = "e3b0c442...")
        String digitalSignature,

        @Schema(description = "Funding source type", example = "CARD")
        FundingType fundingType,

        @Schema(description = "Card ID if card-funded", example = "10")
        Long cardId,

        @Schema(description = "Card last 4 digits if card-funded", example = "0010")
        String cardLast4,

        @Schema(description = "Card kind", example = "LINKED")
        CardKind cardKind,

        @Schema(description = "Friendly payment method display", example = "Paid with card •••• 0010 (TEST CARD)")
        String paymentMethod
) {

    public record ReceiptParty(
            Long id,
            String displayName,
            String maskedPhone,
            String bankCode
    ) {}

    /**
     * Backward-compatible 17-arg constructor.
     */
    public ReceiptResponse(
            String receiptNumber,
            Long transferId,
            String reference,
            String bankTxRef,
            TransactionStatus status,
            BigDecimal amount,
            String currency,
            BigDecimal fee,
            BigDecimal totalDebited,
            ReceiptParty sender,
            ReceiptParty receiver,
            TransferInitiatedVia initiatedVia,
            String note,
            Instant createdAt,
            Instant completedAt,
            boolean simulated,
            String digitalSignature
    ) {
        this(
                receiptNumber, transferId, reference, bankTxRef, status, amount, currency,
                fee, totalDebited, sender, receiver, initiatedVia, note, createdAt, completedAt,
                simulated, digitalSignature, FundingType.ACCOUNT, null, null, null, "Paid with bank account"
        );
    }

    public static ReceiptResponse from(TransactionRecord transfer) {
        String receiptNum = "REC-" + (transfer.reference() != null && transfer.reference().length() >= 8
                ? transfer.reference().substring(0, 8).toUpperCase()
                : "TX" + transfer.id());

        String maskedSenderPhone = transfer.senderPhone() != null && transfer.senderPhone().length() >= 7
                ? transfer.senderPhone().substring(0, 6) + "***" + transfer.senderPhone().substring(transfer.senderPhone().length() - 4)
                : "***";
        String maskedReceiverPhone = transfer.receiverPhone() != null && transfer.receiverPhone().length() >= 7
                ? transfer.receiverPhone().substring(0, 6) + "***" + transfer.receiverPhone().substring(transfer.receiverPhone().length() - 4)
                : "***";

        ReceiptParty senderParty = new ReceiptParty(
                transfer.senderId(),
                transfer.senderFullName(),
                maskedSenderPhone,
                transfer.senderBankCode()
        );

        ReceiptParty receiverParty = new ReceiptParty(
                transfer.receiverId(),
                transfer.receiverFullName(),
                maskedReceiverPhone,
                transfer.receiverBankCode()
        );

        BigDecimal fee = transfer.fee() != null ? transfer.fee().setScale(2) : BigDecimal.ZERO.setScale(2);
        BigDecimal total = transfer.totalAmount() != null ? transfer.totalAmount() : transfer.amount().add(fee);

        String signature = computeSignature(transfer.reference(), transfer.amount(), transfer.status(), transfer.createdAt());

        FundingType ft = transfer.fundingType() != null ? transfer.fundingType() : FundingType.ACCOUNT;
        String paymentMethod;
        if (ft == FundingType.CARD) {
            String last4 = transfer.cardLast4() != null ? transfer.cardLast4() : "••••";
            paymentMethod = "Paid with card •••• " + last4 + " (TEST CARD)";
        } else {
            paymentMethod = "Paid with bank account";
        }

        return new ReceiptResponse(
                receiptNum,
                transfer.id(),
                transfer.reference(),
                transfer.bankTxRef(),
                transfer.status(),
                transfer.amount(),
                transfer.currency(),
                fee,
                total,
                senderParty,
                receiverParty,
                transfer.initiatedVia(),
                transfer.note(),
                transfer.createdAt(),
                transfer.updatedAt(),
                transfer.simulated(),
                signature,
                ft,
                transfer.cardId(),
                transfer.cardLast4(),
                transfer.cardKind(),
                paymentMethod
        );
    }

    public static ReceiptResponse from(Transfer transfer) {
        String receiptNum = "REC-" + (transfer.getReference() != null && transfer.getReference().length() >= 8
                ? transfer.getReference().substring(0, 8).toUpperCase()
                : "TX" + transfer.getId());

        ReceiptParty senderParty = new ReceiptParty(
                transfer.getSender().getId(),
                transfer.getSender().getFullName(),
                transfer.getSender().getMaskedPhone(),
                transfer.getSenderBankCode()
        );

        ReceiptParty receiverParty = new ReceiptParty(
                transfer.getReceiver().getId(),
                transfer.getReceiver().getFullName(),
                transfer.getReceiver().getMaskedPhone(),
                transfer.getReceiverBankCode()
        );

        BigDecimal fee = transfer.getFee() != null ? transfer.getFee().setScale(2) : BigDecimal.ZERO.setScale(2);
        BigDecimal total = transfer.getTotalAmount() != null ? transfer.getTotalAmount() : transfer.getAmount().add(fee);

        String signature = computeSignature(transfer);

        FundingType ft = transfer.getFundingType() != null ? transfer.getFundingType() : FundingType.ACCOUNT;
        String paymentMethod;
        if (ft == FundingType.CARD) {
            String last4 = transfer.getCardLast4() != null ? transfer.getCardLast4() : "••••";
            paymentMethod = "Paid with card •••• " + last4 + " (TEST CARD)";
        } else {
            paymentMethod = "Paid with bank account";
        }

        return new ReceiptResponse(
                receiptNum,
                transfer.getId(),
                transfer.getReference(),
                transfer.getBankTxRef(),
                transfer.getStatus(),
                transfer.getAmount(),
                transfer.getCurrency(),
                fee,
                total,
                senderParty,
                receiverParty,
                transfer.getInitiatedVia(),
                transfer.getNote(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt(),
                transfer.isSimulated(),
                signature,
                ft,
                transfer.getCardId(),
                transfer.getCardLast4(),
                transfer.getCardKind(),
                paymentMethod
        );
    }

    private static String computeSignature(Transfer transfer) {
        return computeSignature(transfer.getReference(), transfer.getAmount(),
                transfer.getStatus(), transfer.getCreatedAt());
    }

    private static String computeSignature(String reference, BigDecimal amount,
                                           TransactionStatus status, Instant createdAt) {
        try {
            String raw = String.join(":",
                    reference != null ? reference : "",
                    amount != null ? amount.toPlainString() : "0.00",
                    status != null ? status.name() : "",
                    createdAt != null ? createdAt.toString() : ""
            );
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "SIG_UNAVAILABLE";
        }
    }
}
