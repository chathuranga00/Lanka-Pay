package com.lankapay.qr.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lankapay.qr.domain.QrCode;
import com.lankapay.qr.domain.QrStatus;
import com.lankapay.qr.domain.QrType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Resolved recipient details from scanning an opaque QR reference")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResolvedQrRecipientResponse(

        @Schema(description = "QR reference scanned", example = "QREF-8f12a3bc-...")
        String reference,

        @Schema(description = "QR type: STATIC or DYNAMIC", example = "DYNAMIC")
        QrType qrType,

        @Schema(description = "Resolved recipient user ID for transfer initiation", example = "42")
        Long recipientId,

        @Schema(description = "Recipient display name", example = "Kamal P.")
        String recipientDisplayName,

        @Schema(description = "Masked phone of recipient", example = "+9477***4567")
        String maskedPhone,

        @Schema(description = "Preset amount if dynamic (null if static — payer inputs amount)", example = "1500.00")
        BigDecimal amount,

        @Schema(description = "Currency", example = "LKR")
        String currency,

        @Schema(description = "Note from QR creator", example = "Table 5 bill")
        String note,

        @Schema(description = "Expiry timestamp (null for static QR)")
        Instant expiresAt,

        @Schema(description = "Current status", example = "ACTIVE")
        QrStatus status,

        @Schema(description = "True if one-time dynamic QR", example = "true")
        boolean oneTimeUse,

        @Schema(description = "Instruction message explaining that scan only resolves recipient", example = "Recipient resolved. Please confirm and authorize payment via transfer endpoint.")
        String message
) {
    public static ResolvedQrRecipientResponse from(QrCode qr) {
        String name = qr.getMerchant() != null ? qr.getMerchant().getFullName() : null;
        String phoneMasked = qr.getMerchant() != null ? maskPhone(qr.getMerchant().getPhone()) : null;

        return new ResolvedQrRecipientResponse(
                qr.getReference(),
                qr.getQrType(),
                qr.getMerchant() != null ? qr.getMerchant().getId() : null,
                name,
                phoneMasked,
                qr.getAmount(),
                qr.getCurrency(),
                qr.getNote(),
                qr.getExpiresAt(),
                qr.getStatus(),
                qr.isOneTimeUse(),
                "Recipient resolved successfully. Proceed with transfer confirmation and authorization."
        );
    }

    public static ResolvedQrRecipientResponse from(com.lankapay.qr.domain.QrCodeRecord qr, String name, String phone) {
        String phoneMasked = maskPhone(phone);
        return new ResolvedQrRecipientResponse(
                qr.reference(),
                qr.qrType(),
                qr.merchantId(),
                name,
                phoneMasked,
                qr.amount(),
                qr.currency(),
                qr.note(),
                qr.expiresAt(),
                qr.status(),
                qr.oneTimeUse(),
                "Recipient resolved successfully. Proceed with transfer confirmation and authorization."
        );
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        int len = phone.length();
        int prefixLen = Math.min(5, len - 4);
        return phone.substring(0, prefixLen) + "***" + phone.substring(len - 4);
    }
}
