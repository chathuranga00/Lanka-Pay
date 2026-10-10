package com.lankapay.identity.web;

import com.lankapay.identity.web.DiscoveryRequest;
import com.lankapay.identity.web.PaymentProfileMeResponse;
import com.lankapay.identity.web.PaymentProfileResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment-profile endpoints.
 *
 * The payment profile is the user's entry in the phone-number registry
 * ({@code reg_registry}). It is created when the user successfully verifies
 * their phone via {@code /api/v1/auth/verify-phone}.
 *
 * Future endpoints on this controller will handle:
 *   - Updating discoverable flag (privacy controls)
 *   - Linking / unlinking bank accounts to the profile
 */
@RestController
@RequestMapping("/api/v1/payment-profile")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Payment Profile", description = "Manage the authenticated user's payment profile")
public class PaymentProfileController {

    private final PhoneVerificationService phoneVerificationService;

    public PaymentProfileController(PhoneVerificationService phoneVerificationService) {
        this.phoneVerificationService = phoneVerificationService;
    }

    // ── GET /phone ─────────────────────────────────────────────

    @Operation(
        summary = "Get payment profile",
        description = """
            Returns the authenticated user's payment profile (verified phone + discoverability).
            Returns 404 PHONE_NOT_VERIFIED if the user has not yet completed phone verification.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Payment profile returned"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Phone not yet verified",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/phone")
    public ResponseEntity<PaymentProfileResponse> getPhone(
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        PaymentProfileResponse response = phoneVerificationService
                .getOrInitPaymentProfile(userId, extractIp(httpRequest));
        return ResponseEntity.ok(response);
    }

    // ── GET /me ────────────────────────────────────────────────

    @Operation(
        summary = "Get full payment profile",
        description = "Returns the authenticated user's full profile: masked phone, "
                      + "discoverability, verification status, and safe display name.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Full profile returned"),
        @ApiResponse(responseCode = "404", description = "Phone not yet verified",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<PaymentProfileMeResponse> getMe(
            @AuthenticationPrincipal Long userId) {

        return ResponseEntity.ok(phoneVerificationService.getMe(userId));
    }

    // ── PATCH /discovery ───────────────────────────────────────

    @Operation(
        summary = "Update discovery setting",
        description = "Enable or disable whether this user's phone number can be found "
                      + "by other users via the lookup endpoint.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Discovery setting updated"),
        @ApiResponse(responseCode = "400", description = "Validation error",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Phone not yet verified",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/discovery")
    public ResponseEntity<PaymentProfileResponse> updateDiscovery(
            @Valid @RequestBody DiscoveryRequest request,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        PaymentProfileResponse response = phoneVerificationService.setDiscovery(
                userId, request.discoverable(), extractIp(httpRequest));
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
