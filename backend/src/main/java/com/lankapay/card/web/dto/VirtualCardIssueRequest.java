package com.lankapay.card.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to issue a new virtual card tied to a linked bank account")
public record VirtualCardIssueRequest(
        @NotNull(message = "linkedBankAccountId is required")
        @Schema(description = "ID of the user's active linked bank account backing this card", example = "101")
        Long linkedBankAccountId,

        @Size(max = 50, message = "nickname must not exceed 50 characters")
        @Schema(description = "Optional nickname for the new virtual card", example = "Online Subscriptions")
        String nickname,

        @Schema(description = "User's 6-digit PIN for step-up auth (can alternatively pass X-Step-Up-Pin header)", example = "123456")
        String pin
) {
    @Override
    public String toString() {
        return "VirtualCardIssueRequest[linkedBankAccountId=" + linkedBankAccountId
                + ", nickname=" + nickname + ", pin=***]";
    }
}
