package com.lankapay.transfer.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Request body for quote generation before transfer")
public record QuoteRequest(

        @Schema(description = "Payee user ID in LankaPay (optional if payeePhone is provided)", example = "42")
        Long payeeId,

        @Schema(description = "Payee phone number (optional if payeeId is provided)", example = "+94771234567")
        String payeePhone,

        @Schema(description = "Transfer amount in LKR", example = "1500.00")
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        BigDecimal amount,

        @Schema(description = "Currency code (default LKR)", example = "LKR")
        String currency
) {}
