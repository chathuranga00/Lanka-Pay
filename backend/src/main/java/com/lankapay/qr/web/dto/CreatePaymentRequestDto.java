package com.lankapay.qr.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Request body to create a payment request")
public record CreatePaymentRequestDto(

        @Schema(description = "Payment amount in LKR", example = "2500.00")
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        BigDecimal amount,

        @Schema(description = "Currency code (default LKR)", example = "LKR")
        String currency,

        @Schema(description = "Target payer user ID (optional: if requesting from a specific user)", example = "42")
        Long payerId,

        @Schema(description = "Target payer phone number (optional: if requesting by phone)", example = "+94771234567")
        String payerPhone,

        @Schema(description = "Description or invoice note", example = "Invoice #1042")
        @Size(max = 255, message = "note must not exceed 255 characters")
        String note,

        @Schema(description = "Validity in minutes (default 15 minutes)", example = "15")
        Integer validityMinutes
) {
    public int resolvedValidityMinutes() {
        return (validityMinutes != null && validityMinutes > 0) ? validityMinutes : 15;
    }
}
