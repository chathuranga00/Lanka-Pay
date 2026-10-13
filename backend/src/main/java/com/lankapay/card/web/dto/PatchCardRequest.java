package com.lankapay.card.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to update card nickname, limits, or online payment setting")
public record PatchCardRequest(
        @Size(max = 50, message = "nickname must not exceed 50 characters")
        @Schema(description = "Updated card nickname", example = "Grocery Card")
        String nickname,

        @Positive(message = "perPaymentLimitMinor must be positive")
        @Schema(description = "Updated per-payment limit in LKR minor units (raising requires step-up auth)", example = "2500000")
        Long perPaymentLimitMinor,

        @Positive(message = "dailyLimitMinor must be positive")
        @Schema(description = "Updated daily limit in LKR minor units (raising requires step-up auth)", example = "10000000")
        Long dailyLimitMinor,

        @Schema(description = "Enable or disable online transactions on this card", example = "true")
        Boolean onlinePaymentsEnabled,

        @Schema(description = "User's 6-digit PIN for step-up auth (required if raising limits; can alternatively pass X-Step-Up-Pin header)", example = "123456")
        String pin
) {
    @Override
    public String toString() {
        return "PatchCardRequest[nickname=" + nickname
                + ", perPaymentLimitMinor=" + perPaymentLimitMinor
                + ", dailyLimitMinor=" + dailyLimitMinor
                + ", onlinePaymentsEnabled=" + onlinePaymentsEnabled
                + ", pin=***]";
    }
}
