package com.lankapay.bank.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body to update mutable bank account metadata. "
        + "Only non-null fields are applied.")
public record PatchBankAccountRequest(

        @Schema(description = "User-assigned label (null = no change, blank string = clear label)",
                example = "My Savings")
        @Size(max = 80)
        String label,

        @Schema(description = "Whether this account appears in the payment registry",
                example = "true")
        Boolean discoverable,

        @Schema(description = "Whether this account can receive incoming transfers",
                example = "true")
        Boolean receivingEnabled
) {}
