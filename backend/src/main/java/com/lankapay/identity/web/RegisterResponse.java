package com.lankapay.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Response after a successful registration request")
public record RegisterResponse(

        @Schema(description = "ID of the newly created user", example = "1")
        Long userId,

        @Schema(description = "When the OTP sent to the phone will expire")
        Instant otpExpiresAt
) {}
