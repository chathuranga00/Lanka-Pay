package com.lankapay.simvault.web;

import com.lankapay.virtualcard.provider.TokenizeResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** HTTP response for POST /sim-vault/v1/tokenize. No PAN, CVV, or full expiry string. */
@Schema(description = "Opaque card token issued by the simulated vault")
public record TokenizeHttpResponse(

        @Schema(description = "Opaque token to pass to the card module for linking")
        String  cardToken,

        @Schema(description = "Last 4 digits of the card number", example = "0011")
        String  last4,

        @Schema(description = "Expiry month (MM)", example = "12")
        String  expMonth,

        @Schema(description = "Expiry year (YY)", example = "27")
        String  expYear,

        @Schema(description = "Brand label — always TEST CARD in simulated mode",
                example = "TEST CARD")
        String  brandLabel,

        @Schema(description = "Always true in simulated mode")
        boolean simulated,

        @Schema(description = "When this token expires if not linked")
        Instant tokenExpiresAt
) {
    public static TokenizeHttpResponse from(TokenizeResponse r) {
        return new TokenizeHttpResponse(
                r.cardToken(), r.last4(), r.expMonth(), r.expYear(),
                r.brandLabel(), r.simulated(), r.tokenExpiresAt());
    }
}
