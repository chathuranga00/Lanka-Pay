package com.lankapay.bank.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body to remove (soft-delete) a linked bank account")
public record RemoveBankAccountRequest(

        @Schema(description = "Must be true — confirms the user understands the account will be removed")
        @NotNull(message = "consentGiven is required")
        Boolean consentGiven
) {}
