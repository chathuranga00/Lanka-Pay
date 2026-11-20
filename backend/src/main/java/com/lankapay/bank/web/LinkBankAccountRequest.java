package com.lankapay.bank.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body to link a new bank account")
public record LinkBankAccountRequest(

        @Schema(description = "Institution code of the bank", example = "SIMULATED_BANK_A")
        @NotBlank(message = "institutionCode is required")
        String institutionCode,

        @Schema(description = "Full bank account number — never stored in plaintext",
                example = "101001")
        @NotBlank(message = "accountNumber is required")
        String accountNumber,

        @Schema(description = "Account holder name as it appears at the bank",
                example = "Kamal Perera")
        @NotBlank(message = "accountName is required")
        @Size(min = 2, max = 100)
        String accountName,

        @Schema(description = "Optional user-assigned label", example = "My Salary Account")
        @Size(max = 80)
        String label,

        @Schema(description = "Must be true — confirms the user read and accepted the consent text")
        @NotNull(message = "consentGiven is required")
        Boolean consentGiven
) {}
