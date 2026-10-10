package com.lankapay.transfer.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Quote details for a proposed transfer")
public record QuoteResponse(

        @Schema(description = "Unique quote reference to lock in rates", example = "QUO-8f12a3bc-1234")
        String quoteReference,

        @Schema(description = "Payee user ID", example = "42")
        Long payeeId,

        @Schema(description = "Payee display name", example = "Kamal P.")
        String payeeDisplayName,

        @Schema(description = "Masked phone of payee", example = "+9477***4567")
        String maskedPhone,

        @Schema(description = "Transfer principal amount", example = "1500.00")
        BigDecimal amount,

        @Schema(description = "Calculated fee", example = "0.00")
        BigDecimal fee,

        @Schema(description = "Total debit amount (amount + fee)", example = "1500.00")
        BigDecimal totalAmount,

        @Schema(description = "Currency", example = "LKR")
        String currency,

        @Schema(description = "Quote expiration timestamp")
        Instant expiresAt,

        @Schema(description = "Always true in prototype", example = "true")
        boolean simulated
) {}
