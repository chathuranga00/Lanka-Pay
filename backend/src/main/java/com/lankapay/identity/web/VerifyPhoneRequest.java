package com.lankapay.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Request body to verify a phone number with the OTP that was sent")
public record VerifyPhoneRequest(

        @Schema(description = "Phone number in any recognised SL format",
                example = "0771234567")
        @NotBlank(message = "phone is required")
        String phone,

        @Schema(description = "6-digit OTP received via SMS", example = "847291")
        @NotBlank(message = "otp is required")
        @Pattern(regexp = "^\\d{6}$", message = "otp must be exactly 6 digits")
        String otp
) {}
