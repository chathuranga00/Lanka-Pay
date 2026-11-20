package com.lankapay.simvault.web;

import com.lankapay.audit.service.AuditService;
import com.lankapay.common.filter.CorrelationIdFilter;
import com.lankapay.common.web.ErrorResponse;
import com.lankapay.simvault.logging.CardSensitiveBodyFilter;
import com.lankapay.virtualcard.provider.CardProvider;
import com.lankapay.virtualcard.provider.TokenizeRequest;
import com.lankapay.virtualcard.provider.TokenizeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Simulated Card Vault REST endpoint.
 *
 * Active only when {@code app.mode=SIMULATED}.
 *
 * SECURITY rules enforced here:
 * - Requires a valid LankaPay JWT (authenticated user) — abuse is attributable.
 * - The request body is NEVER logged ({@link CardSensitiveBodyFilter} suppresses it).
 * - No PAN, CVV, or expiry values ever appear in logs, responses, or stack traces.
 * - The response contains ONLY: cardToken, last4, expiry, brandLabel, simulated, tokenExpiresAt.
 * - A generic 422 is returned for ALL card rejection reasons (anti-enumeration).
 */
@RestController
@RequestMapping("/sim-vault/v1")
@ConditionalOnProperty(name = "app.mode", havingValue = "SIMULATED", matchIfMissing = true)
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Simulated Card Vault",
     description = "TEST DATA ONLY. Tokenize seeded test cards. Never use real card numbers.")
public class SimVaultController {

    private static final Logger log = LoggerFactory.getLogger(SimVaultController.class);

    private final CardProvider cardProvider;
    private final AuditService auditService;

    public SimVaultController(CardProvider cardProvider, AuditService auditService) {
        this.cardProvider = cardProvider;
        this.auditService  = auditService;
    }

    @Operation(
        summary = "Tokenize a test card",
        description = """
            Accepts ONLY seeded TEST cards (see docs/test-cards.md).
            Returns an opaque cardToken that can be used to link the card to a LankaPay account.
            The token expires in 15 minutes if not used.

            ⚠ TEST DATA ONLY — never enter a real card number.
            All responses carry simulated=true.
            Brand label is always "TEST CARD".
            Request body is never logged.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Card tokenized successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error (field format)",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Card rejected (generic — no detail revealed)",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Rate limit or lockout",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/tokenize")
    public ResponseEntity<TokenizeHttpResponse> tokenize(
            @Valid @RequestBody TokenizeHttpRequest httpReq,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest servletRequest) {

        // Never log: PAN, CVV, or full card number
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        String opRef = UUID.randomUUID().toString();
        String ip    = extractIp(servletRequest);

        log.info("[SIMVAULT] tokenize request userId={} correlationId={}", userId, correlationId);

        // Build internal request — PAN + CVV are in this object and will be discarded after use
        TokenizeRequest internalReq = new TokenizeRequest(
                httpReq.pan(), httpReq.expMonth(), httpReq.expYear(),
                httpReq.cvv(), httpReq.holderName(), userId, opRef, ip);

        TokenizeResponse result = cardProvider.tokenize(internalReq);

        // Audit: no PAN/CVV in detailJson
        auditService.log(userId, "VAULT_TOKENIZE_SUCCESS", "VAULT_CARD", null,
                "{\"last4\":\"" + result.last4() + "\",\"simulated\":true}", ip);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TokenizeHttpResponse.from(result));
    }

    private String extractIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
