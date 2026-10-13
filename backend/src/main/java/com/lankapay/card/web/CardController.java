package com.lankapay.card.web;

import com.lankapay.card.domain.Card;
import com.lankapay.card.service.CardService;
import com.lankapay.card.web.dto.*;
import com.lankapay.common.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for linked and virtual card management.
 *
 * Base Path: /api/v1/cards
 *
 * Security & PCI Compliance:
 * - Requires valid JWT for all endpoints.
 * - Object-level authorization: users can only see/modify their own cards; other users get 404.
 * - Sensitive endpoints require step-up authentication (X-Step-Up-Pin header or pin in body).
 * - Reveals are limited to virtual cards, rate limited, and returned with Cache-Control: no-store.
 * - Absolutely NO PAN, CVV, or PIN are ever logged or persisted.
 */
@RestController
@RequestMapping("/api/v1/cards")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Cards", description = "Link, verify, view, manage limits, freeze, reveal, and remove cards")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    // ── 1. POST /cards (Link card) ─────────────────────────────

    @Operation(summary = "Link a new card",
               description = "Validates the cardToken with the simulator vault, requires explicit consent and step-up auth, and initiates bank ownership challenge OTP. Card is saved with status PENDING_VERIFICATION.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Card linked, challenge initiated"),
        @ApiResponse(responseCode = "400", description = "Validation error or consent missing",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials or PIN",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Step-up auth PIN required or token belongs to another user",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Card already linked",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<CardResponse> linkCard(
            @Valid @RequestBody CreateCardRequest request,
            @RequestHeader(value = "X-Step-Up-Pin", required = false) String stepUpPinHeader,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        Card card = cardService.linkCard(userId, request, stepUpPinHeader, extractIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(CardResponse.from(card));
    }

    // ── 2. POST /cards/{id}/verify (Verify challenge) ──────────

    @Operation(summary = "Verify card challenge OTP",
               description = "Confirms the simulated ownership challenge with OTP and transitions card status to ACTIVE.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Card verified and ACTIVE"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired OTP",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Card not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Max verification attempts exceeded",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/verify")
    public ResponseEntity<CardResponse> verifyCard(
            @PathVariable Long id,
            @Valid @RequestBody VerifyCardRequest request,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        Card card = cardService.verifyCard(id, userId, request.otp(), extractIp(httpRequest));
        return ResponseEntity.ok(CardResponse.from(card));
    }

    // ── 3. GET /cards (List cards) ─────────────────────────────

    @Operation(summary = "List cards",
               description = "Returns masked data for all cards owned by the authenticated user.")
    @ApiResponse(responseCode = "200", description = "List of user's cards")
    @GetMapping
    public ResponseEntity<List<CardResponse>> listCards(
            @AuthenticationPrincipal Long userId) {

        List<CardResponse> list = cardService.listCards(userId).stream()
                .map(CardResponse::from)
                .toList();
        return ResponseEntity.ok(list);
    }

    // ── 4. GET /cards/{id} (Get single card) ───────────────────

    @Operation(summary = "Get card details",
               description = "Returns masked data for a specific card. Returns 404 if not found or owned by another user.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Card details (masked)"),
        @ApiResponse(responseCode = "404", description = "Card not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<CardResponse> getCard(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {

        Card card = cardService.getCard(id, userId);
        return ResponseEntity.ok(CardResponse.from(card));
    }

    // ── 5. PATCH /cards/{id} (Update nickname/limits) ──────────

    @Operation(summary = "Update card settings and limits",
               description = "Updates nickname, onlinePaymentsEnabled, and spending limits. Lowering limits requires no step-up; raising limits requires step-up PIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Card updated"),
        @ApiResponse(responseCode = "400", description = "Validation error or limit exceeds risk cap",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Step-up auth required to raise limits",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Card not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}")
    public ResponseEntity<CardResponse> patchCard(
            @PathVariable Long id,
            @Valid @RequestBody PatchCardRequest request,
            @RequestHeader(value = "X-Step-Up-Pin", required = false) String stepUpPinHeader,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        Card card = cardService.patchCard(id, userId, request, stepUpPinHeader, extractIp(httpRequest));
        return ResponseEntity.ok(CardResponse.from(card));
    }

    // ── 6. POST /cards/{id}/freeze ─────────────────────────────

    @Operation(summary = "Freeze card",
               description = "Temporarily freezes an active card, blocking any new transactions.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Card frozen"),
        @ApiResponse(responseCode = "400", description = "Card not active",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Card not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/freeze")
    public ResponseEntity<CardResponse> freezeCard(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        Card card = cardService.freezeCard(id, userId, extractIp(httpRequest));
        return ResponseEntity.ok(CardResponse.from(card));
    }

    // ── 7. POST /cards/{id}/unfreeze ───────────────────────────

    @Operation(summary = "Unfreeze card",
               description = "Unfreezes a previously frozen card. Requires step-up authentication.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Card unfrozen"),
        @ApiResponse(responseCode = "400", description = "Card not frozen",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Step-up auth required",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Card not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/unfreeze")
    public ResponseEntity<CardResponse> unfreezeCard(
            @PathVariable Long id,
            @RequestHeader(value = "X-Step-Up-Pin", required = false) String stepUpPinHeader,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        Card card = cardService.unfreezeCard(id, userId, null, stepUpPinHeader, extractIp(httpRequest));
        return ResponseEntity.ok(CardResponse.from(card));
    }

    // ── 8. POST /cards/{id}/reveal (Virtual cards only) ────────

    @Operation(summary = "Reveal virtual card details",
               description = "Returns ephemeral card details (PAN, CVV, expiry) for virtual cards only. Requires step-up auth, rate-limited, valid for 30 seconds. Response is never cached (Cache-Control: no-store). Linked cards are refused.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Virtual card details returned"),
        @ApiResponse(responseCode = "400", description = "Refused for linked cards",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Step-up PIN required",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Card not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/reveal")
    public ResponseEntity<CardRevealResponse> revealCard(
            @PathVariable Long id,
            @RequestHeader(value = "X-Step-Up-Pin", required = false) String stepUpPinHeader,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        CardRevealResponse response = cardService.revealCard(id, userId, null, stepUpPinHeader, extractIp(httpRequest));

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(response);
    }

    // ── 9. POST /cards/virtual (Issue new virtual card) ────────

    @Operation(summary = "Issue a new virtual card",
               description = "Requests the simulator to issue a new virtual card backed by one of the user's verified linked bank accounts. Requires step-up authentication.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Virtual card issued and active"),
        @ApiResponse(responseCode = "400", description = "Linked bank account not active or invalid",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Step-up PIN required",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Linked bank account not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/virtual")
    public ResponseEntity<CardResponse> issueVirtualCard(
            @Valid @RequestBody VirtualCardIssueRequest request,
            @RequestHeader(value = "X-Step-Up-Pin", required = false) String stepUpPinHeader,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        Card card = cardService.issueVirtualCard(userId, request, stepUpPinHeader, extractIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(CardResponse.from(card));
    }

    // ── 10. DELETE /cards/{id} (Remove card) ───────────────────

    @Operation(summary = "Remove a card",
               description = "Soft-deletes the card (status -> REMOVED), preventing new payments while preserving pending transactions.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Card removed"),
        @ApiResponse(responseCode = "404", description = "Card not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Card already removed",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeCard(
            @PathVariable Long id,
            @RequestHeader(value = "X-Step-Up-Pin", required = false) String stepUpPinHeader,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        cardService.removeCard(id, userId, stepUpPinHeader, extractIp(httpRequest));
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
}
