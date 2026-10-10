package com.lankapay.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Response after a phone verification OTP is sent")
public record PhoneVerifyResponse(

        @Schema(description = "Masked phone number that will receive the OTP",
                example = "+9477***4567")
        String maskedPhone,

        @Schema(description = "When the OTP expires")
        Instant otpExpiresAt
) {}
