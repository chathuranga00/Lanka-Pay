package com.lankapay.transfer.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lankapay.transfer.domain.FundingSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Quote details for a proposed transfer")
@JsonInclude(JsonInclude.Include.NON_NULL)
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

        @Schema(description = "Clearly labelled fee description", example = "Simulated Card Processing Fee")
        String feeLabel,

        @Schema(description = "Total debit amount (amount + fee)", example = "1500.00")
        BigDecimal totalAmount,

        @Schema(description = "Card remaining daily limit in LKR (if card-funded)", example = "185000.00")
        BigDecimal remainingDailyLimit,

        @Schema(description = "Funding source", example = "{\"type\":\"CARD\",\"id\":10}")
        FundingSource fundingSource,

        @Schema(description = "Currency", example = "LKR")
        String currency,

        @Schema(description = "Quote expiration timestamp")
        Instant expiresAt,

        @Schema(description = "Always true in prototype", example = "true")
        boolean simulated
) {

    /**
     * Backward-compatible 10-arg constructor.
     */
    public QuoteResponse(
            String quoteReference,
            Long payeeId,
            String payeeDisplayName,
            String maskedPhone,
            BigDecimal amount,
            BigDecimal fee,
            BigDecimal totalAmount,
            String currency,
            Instant expiresAt,
            boolean simulated
    ) {
        this(
                quoteReference, payeeId, payeeDisplayName, maskedPhone, amount, fee,
                fee != null && fee.compareTo(BigDecimal.ZERO) > 0 ? "Simulated Card Fee" : "Standard Transfer Fee",
                totalAmount, null, null, currency, expiresAt, simulated
        );
    }
}
