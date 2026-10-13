package com.lankapay.card.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ephemeral virtual card details returned strictly on authorized reveal requests.
 * MUST never be cached or logged.
 */
@Schema(description = "Ephemeral virtual card details (expires in 30 seconds)")
public record CardRevealResponse(
        @Schema(description = "Card identifier", example = "1001")
        Long cardId,

        @Schema(description = "Last 4 digits", example = "0010")
        String last4,

        @Schema(description = "Simulated PAN for testing virtual card entry", example = "9900XXXXXXXX0010")
        String pan,

        @Schema(description = "Simulated CVV", example = "123")
        String cvv,

        @Schema(description = "Expiration month", example = "12")
        String expMonth,

        @Schema(description = "Expiration year", example = "29")
        String expYear,

        @Schema(description = "Brand label", example = "TEST CARD")
        String brandLabel,

        @Schema(description = "Number of seconds this view is valid for before client must clear display", example = "30")
        int expiresInSeconds,

        @Schema(description = "Whether card is simulated", example = "true")
        boolean simulated
) {
    @Override
    public String toString() {
        return "CardRevealResponse[cardId=" + cardId + ", last4=" + last4
                + ", pan=****" + last4 + ", cvv=***, expMonth=**, expYear=**, brandLabel="
                + brandLabel + ", expiresInSeconds=" + expiresInSeconds + ", simulated=" + simulated + "]";
    }
}
