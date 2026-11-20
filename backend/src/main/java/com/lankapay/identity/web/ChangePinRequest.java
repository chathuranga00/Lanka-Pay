package com.lankapay.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Request body to change the user's PIN")
public record ChangePinRequest(

        @Schema(description = "Current 6-digit PIN", example = "123456")
        @NotBlank(message = "currentPin is required")
        @Pattern(regexp = "^\\d{6}$", message = "currentPin must be exactly 6 digits")
        String currentPin,

        @Schema(description = "New 6-digit PIN", example = "654321")
        @NotBlank(message = "newPin is required")
        @Pattern(regexp = "^\\d{6}$", message = "newPin must be exactly 6 digits")
        String newPin
) {}
