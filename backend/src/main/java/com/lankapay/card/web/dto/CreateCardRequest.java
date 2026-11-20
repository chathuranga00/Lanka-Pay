package com.lankapay.card.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to link an existing card token to the user account")
public record CreateCardRequest(
        @NotBlank(message = "cardToken must not be blank")
        @Schema(description = "Opaque token received from tokenization", example = "550e8400-e29b-41d4-a716-446655440000")
        String cardToken,

        @Size(max = 50, message = "nickname must not exceed 50 characters")
        @Schema(description = "User-assigned label/nickname for the card", example = "My Shopping Card")
        String nickname,

        @Schema(description = "Optional linked bank account ID", example = "101")
        Long linkedBankAccountId,

        @NotNull(message = "consentGiven must be specified")
        @Schema(description = "Explicit user consent for card linking", example = "true")
        Boolean consentGiven,

        @Schema(description = "User's 6-digit PIN for step-up auth (can alternatively pass X-Step-Up-Pin header)", example = "123456")
        String pin
) {
    @Override
    public String toString() {
        return "CreateCardRequest[cardToken=" + cardToken + ", nickname=" + nickname
                + ", linkedBankAccountId=" + linkedBankAccountId
                + ", consentGiven=" + consentGiven + ", pin=***]";
    }
}
