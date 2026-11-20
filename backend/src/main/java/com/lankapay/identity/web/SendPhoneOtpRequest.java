package com.lankapay.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body to send a phone-verification OTP")
public record SendPhoneOtpRequest(

        @Schema(description = "Phone number in any recognised SL format",
                example = "0771234567")
        @NotBlank(message = "phone is required")
        String phone
) {}
