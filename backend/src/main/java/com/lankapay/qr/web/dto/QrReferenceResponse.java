package com.lankapay.qr.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lankapay.qr.domain.QrCode;
import com.lankapay.qr.domain.QrStatus;
import com.lankapay.qr.domain.QrType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Generated QR reference details with opaque payload")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QrReferenceResponse(

        @Schema(description = "Opaque, unguessable reference identifier", example = "QREF-8f12a3bc-1234-...")
        String reference,

        @Schema(description = "QR type: STATIC or DYNAMIC", example = "DYNAMIC")
        QrType qrType,

        @Schema(description = "Opaque QR payload string for barcode encoding (no PII, no account data)", example = "LANKAPAY:QR:QREF-8f12a3bc-...")
        String qrPayload,

        @Schema(description = "Recipient user ID", example = "42")
        Long recipientId,

        @Schema(description = "Recipient display name", example = "Kamal P.")
        String recipientDisplayName,

        @Schema(description = "Masked phone of recipient", example = "+9477***4567")
        String maskedPhone,

        @Schema(description = "Requested amount (null for static QR)", example = "1500.00")
        BigDecimal amount,

        @Schema(description = "Currency", example = "LKR")
        String currency,

        @Schema(description = "Optional note", example = "Table 5 bill")
        String note,

        @Schema(description = "Expiry timestamp (null for static QR)")
        Instant expiresAt,

        @Schema(description = "Whether the reference is one-time use", example = "true")
        boolean oneTimeUse,

        @Schema(description = "Status of QR reference", example = "ACTIVE")
        QrStatus status,

        @Schema(description = "Creation timestamp")
        Instant createdAt
) {
    public static QrReferenceResponse from(QrCode qr) {
        String name = qr.getMerchant() != null ? qr.getMerchant().getFullName() : null;
        String phoneMasked = qr.getMerchant() != null ? maskPhone(qr.getMerchant().getPhone()) : null;

        return new QrReferenceResponse(
                qr.getReference(),
                qr.getQrType(),
                qr.getPayload(),
                qr.getMerchant() != null ? qr.getMerchant().getId() : null,
                name,
                phoneMasked,
                qr.getAmount(),
                qr.getCurrency(),
                qr.getNote(),
                qr.getExpiresAt(),
                qr.isOneTimeUse(),
                qr.getStatus(),
                qr.getCreatedAt()
        );
    }

    public static QrReferenceResponse from(com.lankapay.qr.domain.QrCodeRecord qr, com.lankapay.identity.domain.User merchant) {
        String name = merchant != null ? merchant.getFullName() : null;
        String phoneMasked = merchant != null ? maskPhone(merchant.getPhone()) : null;

        return new QrReferenceResponse(
                qr.reference(),
                qr.qrType(),
                qr.payload(),
                merchant != null ? merchant.getId() : qr.merchantId(),
                name,
                phoneMasked,
                qr.amount(),
                qr.currency(),
                qr.note(),
                qr.expiresAt(),
                qr.oneTimeUse(),
                qr.status(),
                qr.createdAt()
        );
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        int len = phone.length();
        return phone.substring(0, 6) + "***" + phone.substring(len - 4);
    }
}
