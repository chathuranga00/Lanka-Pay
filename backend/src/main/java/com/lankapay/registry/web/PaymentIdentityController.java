package com.lankapay.registry.web;

import com.lankapay.common.exception.RateLimitException;
import com.lankapay.common.web.ErrorResponse;
import com.lankapay.registry.domain.LookupResultType;
import com.lankapay.registry.service.LookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment identity lookup endpoint.
 *
 * Anti-enumeration rules enforced here:
 * - FOUND returns 200; every other outcome returns 404 with a typed resultType.
 *   This means a caller cannot distinguish "phone not registered" from
 *   "phone registered but private" purely from the HTTP status code.
 * - Rate-limit exceptions (429) are the only departure from the 200/404 pattern.
 * - IP is extracted and forwarded for per-IP rate-limiting.
 */
@RestController
@RequestMapping("/api/v1/payment-identities")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Payment Identities", description = "Look up payees by phone number")
@Validated
public class PaymentIdentityController {

    private final LookupService lookupService;

    public PaymentIdentityController(LookupService lookupService) {
        this.lookupService = lookupService;
    }

    @Operation(
        summary = "Look up a payment identity by phone number",
        description = """
            Resolves a phone number to a payee's payment profile.

            **Privacy model:**
            - Always returns 200 for FOUND, 404 for all other states.
            - The `resultType` field in the 404 body distinguishes UX states
              (NOT_FOUND, DISCOVERY_DISABLED, NO_ELIGIBLE_ACCOUNT, PRIVACY_RESTRICTED)
              without revealing whether a phone number is registered.
            - Only masked bank name + last-4-digits are returned — never full account numbers.
            - The payee's display name is abbreviated (first name + last initial).

            **Rate limiting:**
            - Maximum 10 lookups/minute per authenticated user.
            - Maximum 20 lookups/minute per IP address.
            - Sustained high-volume lookups trigger an abuse flag and audit.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Payee found and has eligible accounts"),
        @ApiResponse(responseCode = "400", description = "Invalid phone number format",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Payee not found or not discoverable "
                     + "(see resultType for UX-specific state)",
                     content = @Content(schema = @Schema(implementation = LookupResponse.class))),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/lookup")
    public ResponseEntity<LookupResponse> lookup(
            @RequestParam @NotBlank(message = "phone is required") String phone,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        LookupResponse response = lookupService.lookup(phone, userId, extractIp(httpRequest));

        // FOUND → 200, all other states → 404
        HttpStatus status = response.resultType() == LookupResultType.FOUND
                ? HttpStatus.OK
                : HttpStatus.NOT_FOUND;

        return ResponseEntity.status(status).body(response);
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
