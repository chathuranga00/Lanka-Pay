package com.lankapay.transfer.web.dto;

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
public record ReceiptResponse(
        @Schema(description = "Unique receipt reference", example = "REC-550E8400")
        String receiptNumber,

        @Schema(description = "Internal transfer ID", example = "100")
        Long transferId,

        @Schema(description = "External transfer reference UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        String reference,

        @Schema(description = "Bank adapter provider reference", example = "SIM-TX-9999")
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
        String digitalSignature
) {

    public record ReceiptParty(
            Long id,
            String displayName,
            String maskedPhone,
            String bankCode
    ) {}

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

        BigDecimal fee = BigDecimal.ZERO.setScale(2);
        BigDecimal total = transfer.amount().add(fee);

        String signature = computeSignature(transfer.reference(), transfer.amount(), transfer.status(), transfer.createdAt());

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
                signature
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

        BigDecimal fee = BigDecimal.ZERO.setScale(2);
        BigDecimal total = transfer.getAmount().add(fee);

        String signature = computeSignature(transfer);

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
                signature
        );
    }

    private static String computeSignature(Transfer transfer) {
        return computeSignature(transfer.getReference(), transfer.getAmount(), transfer.getStatus(), transfer.getCreatedAt());
    }

    private static String computeSignature(String reference, BigDecimal amount, TransactionStatus status, Instant createdAt) {
        try {
            String raw = reference + ":" + amount + ":" + status + ":" + createdAt;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "SIGNATURE_UNAVAILABLE";
        }
    }
}
