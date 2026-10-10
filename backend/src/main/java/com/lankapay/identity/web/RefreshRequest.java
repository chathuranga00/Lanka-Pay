package com.lankapay.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body to rotate a refresh token")
public record RefreshRequest(

        @Schema(description = "The refresh token issued at login or previous refresh")
        @NotBlank(message = "refreshToken is required")
        String refreshToken
) {}
