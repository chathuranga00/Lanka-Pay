package com.lankapay.identity.web;

import com.lankapay.common.web.ErrorResponse;
import com.lankapay.identity.service.PhoneVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phone-verification endpoints.
 *
 * Flow:
 *   1. POST /api/v1/auth/verify-phone/send-otp   → send OTP to the user's registered phone
 *   2. POST /api/v1/auth/verify-phone             → submit OTP, activate phone in registry
 *
 * Both require a valid JWT (authenticated user only).
 * Phone numbers are normalised to E.164 inside the service; any recognised SL format is accepted.
 */
@RestController
@RequestMapping("/api/v1/auth/verify-phone")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Phone Verification", description = "Verify a phone number via OTP")
public class PhoneVerificationController {

    private final PhoneVerificationService phoneVerificationService;

    public PhoneVerificationController(PhoneVerificationService phoneVerificationService) {
        this.phoneVerificationService = phoneVerificationService;
    }

    // ── POST /send-otp ─────────────────────────────────────────

    @Operation(
        summary = "Send phone-verification OTP",
        description = """
            Normalises the supplied phone number to E.164, verifies it belongs to the
            authenticated user, enforces resend cooldown and daily cap, then dispatches
            a 6-digit OTP via SMS (SIMULATED in TEST mode).
            The OTP is valid for 5 minutes and may be used exactly once.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OTP sent; returns masked phone and expiry"),
        @ApiResponse(responseCode = "400", description = "Invalid phone format or validation error",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Phone already registered to another account",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Resend cooldown active or daily cap reached",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/send-otp")
    public ResponseEntity<PhoneVerifyResponse> sendOtp(
            @Valid @RequestBody SendPhoneOtpRequest request,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        PhoneVerifyResponse response = phoneVerificationService.sendOtp(
                request.phone(), userId, extractIp(httpRequest));
        return ResponseEntity.ok(response);
    }

    // ── POST / ─────────────────────────────────────────────────

    @Operation(
        summary = "Verify phone with OTP",
        description = """
            Verifies the 6-digit OTP. On success, registers the phone number in the
            payment registry so the user can send and receive payments.
            Each OTP is single-use; replaying a used code returns OTP_EXPIRED.
            After 5 wrong attempts the account is locked for 15 minutes.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Phone verified; payment profile active"),
        @ApiResponse(responseCode = "400", description = "Invalid OTP",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Phone already registered to another account",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "410", description = "OTP expired",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Too many failed OTP attempts",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<PaymentProfileResponse> verifyPhone(
            @Valid @RequestBody VerifyPhoneRequest request,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        PaymentProfileResponse response = phoneVerificationService.verifyPhone(
                request.phone(), request.otp(), userId, extractIp(httpRequest));
        return ResponseEntity.ok(response);
    }

    // ── Helpers ────────────────────────────────────────────────

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
