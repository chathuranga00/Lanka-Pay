package com.lankapay.qr.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lankapay.qr.domain.PaymentRequest;
import com.lankapay.qr.domain.PaymentRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Payment request response details")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentRequestResponse(

        @Schema(description = "Payment request primary ID", example = "501")
        Long id,

        @Schema(description = "Opaque public reference", example = "PREQ-8f12a3bc-...")
        String reference,

        @Schema(description = "Requester (payee) user ID", example = "1")
        Long requesterId,

        @Schema(description = "Requester display name", example = "Kamal Perera")
        String requesterName,

        @Schema(description = "Designated payer user ID (if targeted)", example = "42")
        Long payerId,

        @Schema(description = "Designated payer display name (if targeted)", example = "Nimal Silva")
        String payerName,

        @Schema(description = "Requested amount", example = "2500.00")
        BigDecimal amount,

        @Schema(description = "Currency", example = "LKR")
        String currency,

        @Schema(description = "Invoice note", example = "Invoice #1042")
        String note,

        @Schema(description = "Status of payment request", example = "PENDING")
        PaymentRequestStatus status,

        @Schema(description = "Linked QR reference (if generated)", example = "QREF-8f12a3bc-...")
        String qrReference,

        @Schema(description = "Opaque QR payload", example = "LANKAPAY:QR:QREF-8f12a3bc-...")
        String qrPayload,

        @Schema(description = "Fulfilling transfer ID (if completed)", example = "99")
        Long fulfilledTransferId,

        @Schema(description = "Expiry timestamp")
        Instant expiresAt,

        @Schema(description = "Creation timestamp")
        Instant createdAt
) {
    public static PaymentRequestResponse from(PaymentRequest pr) {
        String requesterName = pr.getRequester() != null ? pr.getRequester().getFullName() : null;
        String payerName = pr.getPayer() != null ? pr.getPayer().getFullName() : null;
        String qrRef = pr.getQrCode() != null ? pr.getQrCode().getReference() : null;
        String qrPayload = pr.getQrCode() != null ? pr.getQrCode().getPayload() : null;

        return new PaymentRequestResponse(
                pr.getId(),
                pr.getReference(),
                pr.getRequester() != null ? pr.getRequester().getId() : null,
                requesterName,
                pr.getPayer() != null ? pr.getPayer().getId() : null,
                payerName,
                pr.getAmount(),
                pr.getCurrency(),
                pr.getNote(),
                pr.getStatus(),
                qrRef,
                qrPayload,
                pr.getFulfilledTransferId(),
                pr.getExpiresAt(),
                pr.getCreatedAt()
        );
    }
}
