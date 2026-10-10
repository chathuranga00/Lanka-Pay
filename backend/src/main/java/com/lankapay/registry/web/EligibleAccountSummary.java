package com.lankapay.registry.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single bank account summary returned inside a lookup response.
 *
 * Security rules:
 * - ONLY bank name + masked number are ever returned (never full account number).
 * - encryptedAccountReference is NEVER included.
 * - accountNumberHash is NEVER included.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A payee's bank account eligible to receive transfers (masked)")
public record EligibleAccountSummary(

        @Schema(description = "Institution code", example = "SIMULATED_BANK_A")
        String institutionCode,

        @Schema(description = "Bank display name", example = "Simulated Bank A [SIMULATED]")
        String bankDisplayName,

        @Schema(description = "Last 4 digits of account number", example = "****1001")
        String maskedNumber,

        @Schema(description = "Whether this is the payee's default account")
        boolean isDefault
) {}
