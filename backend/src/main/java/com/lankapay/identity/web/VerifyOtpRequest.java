package com.lankapay.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request body for OTP verification after registration or login")
public record VerifyOtpRequest(

        @Schema(description = "User ID returned by /register", example = "1")
        @NotNull(message = "userId is required")
        @Positive(message = "userId must be positive")
        Long userId,

        @Schema(description = "6-digit OTP received via SMS", example = "847291")
        @NotBlank(message = "otp is required")
        @Pattern(regexp = "^\\d{6}$", message = "otp must be exactly 6 digits")
        String otp
) {}
