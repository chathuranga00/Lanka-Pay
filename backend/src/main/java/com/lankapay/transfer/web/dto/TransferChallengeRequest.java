package com.lankapay.transfer.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body to complete card OTP challenge for transfer")
public record TransferChallengeRequest(
        @Schema(description = "6-digit OTP code sent to user phone", example = "123456")
        @NotBlank(message = "otp is required")
        String otp
) {}
