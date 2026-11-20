package com.lankapay.identity.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token response returned after successful login, OTP verification, or token refresh")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(

        @Schema(description = "Short-lived JWT access token")
        String accessToken,

        @Schema(description = "Rotating refresh token. Present on login/verify-otp; absent on /refresh response")
        String refreshToken,

        @Schema(description = "Access token lifetime in seconds", example = "1800")
        long expiresIn,

        @Schema(description = "Authenticated user's ID", example = "1")
        Long userId,

        @Schema(description = "User's real registered full name", example = "Kasun Perera")
        String fullName,

        @Schema(description = "User's registered mobile number", example = "+94771234567")
        String phone
) {
    /** Used by /refresh – no new refresh token in response. */
    public static TokenResponse accessOnly(String accessToken, long expiresIn) {
        return new TokenResponse(accessToken, null, expiresIn, null, null, null);
    }

    public static TokenResponse accessOnly(String accessToken, long expiresIn, Long userId, String fullName, String phone) {
        return new TokenResponse(accessToken, null, expiresIn, userId, fullName, phone);
    }

    /** Used by /login and /verify-otp – full token pair. */
    public static TokenResponse full(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, expiresIn, null, null, null);
    }

    public static TokenResponse full(String accessToken, String refreshToken, long expiresIn, Long userId, String fullName, String phone) {
        return new TokenResponse(accessToken, refreshToken, expiresIn, userId, fullName, phone);
    }
}
