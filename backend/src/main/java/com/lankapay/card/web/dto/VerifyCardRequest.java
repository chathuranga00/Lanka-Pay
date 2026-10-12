package com.lankapay.card.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Request to verify ownership of a card with OTP challenge")
public record VerifyCardRequest(
        @NotBlank(message = "OTP code is required")
        @Pattern(regexp = "\\d{6}", message = "OTP must be exactly 6 digits")
        @Schema(description = "6-digit OTP sent to user's phone", example = "123456")
        String otp
) {}
