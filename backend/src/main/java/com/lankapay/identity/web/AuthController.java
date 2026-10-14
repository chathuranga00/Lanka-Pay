package com.lankapay.identity.web;

import com.lankapay.common.web.ErrorResponse;
import com.lankapay.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints.
 *
 * All endpoints that return tokens use the standard {@link TokenResponse}.
 * Errors always use {@link ErrorResponse} with a machine-readable {@code code}.
 *
 * Security rules:
 * - Tokens and passwords are NEVER logged here or in the service layer.
 * - IP address is extracted from the request and passed to AuthService for audit.
 */
@RestController
@RequestMapping({"/api/v1/auth", "/auth"})
@Tag(name = "Authentication", description = "Register, login, token refresh, logout, change PIN")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // ── POST /register ─────────────────────────────────────────

    @Operation(summary = "Register a new user",
               description = "Creates a PENDING_VERIFICATION account and sends a 6-digit OTP via SMS (SIMULATED).")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Registration accepted; OTP sent"),
        @ApiResponse(responseCode = "400", description = "Validation error",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Phone or NIC already registered",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {

        RegisterResponse response = authService.register(request, extractIp(httpRequest));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // ── POST /verify-otp ───────────────────────────────────────

    @Operation(summary = "Verify OTP after registration",
               description = "Validates the 6-digit OTP. On success, activates the account and returns JWT + refresh token.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OTP verified; tokens issued"),
        @ApiResponse(responseCode = "400", description = "Invalid OTP",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "410", description = "OTP expired",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Too many failed OTP attempts",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/verify-otp")
    public ResponseEntity<TokenResponse> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest httpRequest) {

        TokenResponse tokens = authService.verifyOtp(
                request.userId(), request.otp(), extractIp(httpRequest));
        return ResponseEntity.ok(tokens);
    }

    // ── POST /login ────────────────────────────────────────────

    @Operation(summary = "Login with phone + PIN",
               description = "Returns a JWT access token and a rotating refresh token. "
                             + "Accounts are locked for 15 minutes after 5 consecutive failures.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "400", description = "Validation error",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Account frozen or not yet verified",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Account temporarily locked",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        TokenResponse tokens = authService.login(
                request.phone(), request.pin(), extractIp(httpRequest));
        return ResponseEntity.ok(tokens);
    }

    // ── POST /refresh ──────────────────────────────────────────

    @Operation(summary = "Refresh access token",
               description = "Exchanges a valid refresh token for a new access token. "
                             + "The old refresh token is revoked (rotation).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "New access token issued"),
        @ApiResponse(responseCode = "400", description = "Validation error",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Refresh token expired or revoked",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest) {

        TokenResponse tokens = authService.refresh(
                request.refreshToken(), extractIp(httpRequest));
        // Only return the new access token (refresh token is not rotated on /refresh)
        return ResponseEntity.ok(TokenResponse.accessOnly(
                tokens.accessToken(), tokens.expiresIn(), tokens.userId(), tokens.fullName(), tokens.phone()));
    }

    // ── POST /logout ───────────────────────────────────────────

    @Operation(summary = "Logout (revoke refresh token)",
               description = "Revokes the provided refresh token. Requires a valid JWT.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Logged out successfully"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) RefreshRequest request,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        String rawToken = (request != null) ? request.refreshToken() : null;
        authService.logout(rawToken, userId, extractIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    // ── PATCH /change-pin ──────────────────────────────────────

    @Operation(summary = "Change PIN",
               description = "Verifies the current PIN then sets a new one. "
                             + "Revokes all refresh tokens, forcing re-login on all devices. Requires a valid JWT.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "PIN changed successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Wrong current PIN or missing JWT",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/change-pin")
    public ResponseEntity<MessageResponse> changePin(
            @Valid @RequestBody ChangePinRequest request,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        authService.changePin(userId, request.currentPin(), request.newPin(), extractIp(httpRequest));
        return ResponseEntity.ok(new MessageResponse("PIN updated successfully"));
    }

    // ── DELETE /cleanup (Testing / Dev Cleanup) ────────────────
    @Operation(summary = "Clean up test user by phone", description = "Deletes user and phone identity for re-testing registration.")
    @DeleteMapping("/cleanup")
    public ResponseEntity<Void> cleanup(@RequestParam String phone) {
        authService.deleteUserByPhone(phone);
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ────────────────────────────────────────────────

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Simple message wrapper for non-token success responses. */
    public record MessageResponse(String message) {}
}
