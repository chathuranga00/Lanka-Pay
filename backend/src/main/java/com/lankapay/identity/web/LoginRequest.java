package com.lankapay.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Request body for phone + PIN login")
public record LoginRequest(

        @Schema(description = "Registered phone number in E.164 format", example = "+94771234567")
        @NotBlank(message = "phone is required")
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "phone must be in E.164 format")
        String phone,

        @Schema(description = "6-digit PIN", example = "123456")
        @NotBlank(message = "pin is required")
        @Pattern(regexp = "^\\d{6}$", message = "pin must be exactly 6 digits")
        String pin
) {}
