package com.lankapay.transfer.web.dto;

import com.lankapay.transfer.domain.FundingSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Request body to initiate a transfer")
public record InitiateTransferRequest(

        @Schema(description = "Payee user ID (optional if payeePhone provided)", example = "42")
        Long payeeId,

        @Schema(description = "Payee phone number (optional if payeeId provided)", example = "+94771234567")
        String payeePhone,

        @Schema(description = "Transfer amount in LKR", example = "1500.00")
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        BigDecimal amount,

        @Schema(description = "Currency code (default LKR)", example = "LKR")
        String currency,

        @Schema(description = "Quote reference from POST /transfers/quote if available", example = "QUO-8f12a3bc-1234")
        String quoteReference,

        @Schema(description = "Optional note or reference for the transfer", example = "Lunch split")
        @Size(max = 255, message = "note must not exceed 255 characters")
        String note,

        @Schema(description = "6-digit PIN for step-up authentication (can also be passed via X-Step-Up-Pin header)", example = "123456")
        String pin,

        @Schema(description = "Funding source specification (ACCOUNT or CARD)")
        FundingSource fundingSource
) {

    public InitiateTransferRequest(
            Long payeeId,
            String payeePhone,
            BigDecimal amount,
            String currency,
            String quoteReference,
            String note,
            String pin
    ) {
        this(payeeId, payeePhone, amount, currency, quoteReference, note, pin, null);
    }
}
