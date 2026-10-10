package com.lankapay.qr.web.dto;

import com.lankapay.qr.domain.QrType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Request body to generate an opaque QR reference (static or dynamic)")
public record CreateQrReferenceRequest(

        @Schema(description = "Type of QR: STATIC (reusable profile QR) or DYNAMIC (amount + expiry)", example = "DYNAMIC")
        QrType qrType,

        @Schema(description = "Transfer amount (optional for STATIC, required for DYNAMIC)", example = "1500.00")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero if provided")
        BigDecimal amount,

        @Schema(description = "Currency code (default LKR)", example = "LKR")
        String currency,

        @Schema(description = "Optional transaction note", example = "Table 5 bill")
        @Size(max = 255, message = "note must not exceed 255 characters")
        String note,

        @Schema(description = "Validity in minutes for dynamic QR (default 15 min, ignored for static)", example = "15")
        Integer validityMinutes
) {
    public QrType resolvedType() {
        return qrType != null ? qrType : QrType.DYNAMIC;
    }

    public int resolvedValidityMinutes() {
        return (validityMinutes != null && validityMinutes > 0) ? validityMinutes : 15;
    }
}
