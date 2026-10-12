package com.lankapay.simvault.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * HTTP request body for POST /sim-vault/v1/tokenize.
 *
 * SECURITY:
 * - This DTO is NEVER logged (CardSensitiveBodyFilter suppresses body logging).
 * - toString() is intentionally not overridden so it emits the default
 *   Object identity string (no field values) if accidentally logged.
 * - Validation is done here; the VaultCardValidator does a second round
 *   against the seed table.
 */
@Schema(description = "Tokenize a test card number. TEST DATA ONLY — never use real card numbers.")
public record TokenizeHttpRequest(

        @Schema(description = "16-digit TEST card number (Luhn-valid, starts with 99)",
                example = "see docs/test-cards.md")
        @NotBlank(message = "pan is required")
        @Pattern(regexp = "\\d{16}", message = "pan must be exactly 16 digits")
        String pan,

        @Schema(description = "2-digit expiry month (MM)", example = "12")
        @NotBlank(message = "expMonth is required")
        @Pattern(regexp = "0[1-9]|1[0-2]", message = "expMonth must be MM (01–12)")
        String expMonth,

        @Schema(description = "2-digit expiry year (YY)", example = "27")
        @NotBlank(message = "expYear is required")
        @Pattern(regexp = "\\d{2}", message = "expYear must be 2 digits (YY)")
        String expYear,

        @Schema(description = "3–4 digit CVV", example = "see docs/test-cards.md")
        @NotBlank(message = "cvv is required")
        @Pattern(regexp = "\\d{3,4}", message = "cvv must be 3 or 4 digits")
        String cvv,

        @Schema(description = "Card holder name", example = "Test Approved")
        @NotBlank(message = "holderName is required")
        @Size(min = 2, max = 100, message = "holderName must be 2–100 characters")
        String holderName
) {
    // Deliberately no toString() override — prevents accidental logging of PAN/CVV.
}
