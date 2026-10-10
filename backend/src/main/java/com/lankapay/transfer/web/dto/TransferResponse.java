package com.lankapay.transfer.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
        Instant updatedAt
) {

    public static TransferResponse from(Transfer transfer) {
        String receiverName = transfer.getReceiver() != null ? transfer.getReceiver().getFullName() : null;
        String senderPhoneMasked = transfer.getSender() != null ? maskPhone(transfer.getSender().getPhone()) : null;

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
                transfer.getUpdatedAt()
        );
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        int len = phone.length();
        return phone.substring(0, 6) + "***" + phone.substring(len - 4);
    }
}
