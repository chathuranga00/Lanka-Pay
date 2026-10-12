package com.lankapay.transfer.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Funding source specification for quote and transfer initiation:
 * { type: "ACCOUNT" | "CARD", id: <id> }
 */
@Schema(description = "Funding source specification for transfer")
public record FundingSource(
        @Schema(description = "Funding type: ACCOUNT or CARD", example = "CARD")
        @NotNull(message = "type is required")
        FundingType type,

        @Schema(description = "ID of the bank account or card (optional for account if using default)", example = "10")
        Long id
) {
    public static FundingSource account(Long id) {
        return new FundingSource(FundingType.ACCOUNT, id);
    }

    public static FundingSource card(Long id) {
        return new FundingSource(FundingType.CARD, id);
    }
}
