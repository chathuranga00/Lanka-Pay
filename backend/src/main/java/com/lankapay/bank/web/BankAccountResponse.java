package com.lankapay.bank.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lankapay.bank.domain.BankAccount;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * API response for a single bank account.
 *
 * Security rules:
 * - encryptedAccountReference is NEVER included in any response.
 * - accountNumberHash is NEVER included.
 * - Only maskedNumber (****XXXX) is returned.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Bank account details (masked — no plaintext account numbers)")
public record BankAccountResponse(

        @Schema(description = "Internal account ID", example = "7")
        Long id,

        @Schema(description = "Institution code", example = "SIMULATED_BANK_A")
        String institutionCode,

        @Schema(description = "Account holder name", example = "Kamal Perera")
        String accountName,

        @Schema(description = "Last 4 digits masked", example = "****1001")
        String maskedNumber,

        @Schema(description = "Optional user label", example = "My Salary Account")
        String label,

        @Schema(description = "Account status", example = "ACTIVE")
        String status,

        @Schema(description = "Whether account is the default for payments")
        boolean isDefault,

        @Schema(description = "Whether account appears in payment registry")
        boolean discoverable,

        @Schema(description = "Whether account can receive transfers")
        boolean receivingEnabled,

        @Schema(description = "Whether this is a simulated account")
        boolean simulated,

        @Schema(description = "When the account was linked")
        Instant linkedAt
) {
    /** Map a BankAccount entity to this response. */
    public static BankAccountResponse from(BankAccount a, boolean simulated) {
        return new BankAccountResponse(
                a.getId(),
                a.getBankCode(),
                a.getAccountName(),
                a.getMaskedNumber(),
                a.getLabel(),
                a.getStatus().name(),
                a.isDefault(),
                a.isDiscoverable(),
                a.isReceivingEnabled(),
                simulated,
                a.getLinkedAt());
    }
}
