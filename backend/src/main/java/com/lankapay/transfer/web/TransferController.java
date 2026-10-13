package com.lankapay.transfer.web;

import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.web.ErrorResponse;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.Transfer;
import com.lankapay.transfer.service.ReconciliationService;
import com.lankapay.transfer.service.ReversalService;
import com.lankapay.transfer.service.TransferService;
import com.lankapay.transfer.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping({"/api/v1/transfers", "/transfers"})
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Transfers", description = "Quotes, peer-to-peer transfers, history, and reconciliation")
public class TransferController {

    private final TransferService transferService;
    private final ReconciliationService reconciliationService;
    private final ReversalService reversalService;
    private final UserStore userStore;

    public TransferController(TransferService transferService,
                              ReconciliationService reconciliationService,
                              ReversalService reversalService,
                              UserStore userStore) {
        this.transferService = transferService;
        this.reconciliationService = reconciliationService;
        this.reversalService = reversalService;
        this.userStore = userStore;
    }

    // ── POST /transfers/quote ──────────────────────────────────

    @Operation(summary = "Get transfer quote",
               description = "Calculates fees, validates payee, and returns quote locking rates before transfer submission.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Quote generated successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Payee not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Daily limit exceeded",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/quote")
    public ResponseEntity<QuoteResponse> getQuote(
            @Valid @RequestBody QuoteRequest request,
            @AuthenticationPrincipal Long userId) {

        User sender = getUser(userId);
        QuoteResponse quote = transferService.getQuote(request, sender);
        return ResponseEntity.ok(quote);
    }

    // ── POST /transfers ────────────────────────────────────────

    @Operation(summary = "Initiate peer-to-peer transfer",
               description = """
                   Initiates a transfer. Requires:
                   - Idempotency-Key header to prevent duplicate charges.
                   - Step-up authentication (6-digit PIN via X-Step-Up-Pin header or pin in body).
                   - Pre-flight eligibility check and BankAdapter debit/credit confirmation.
                   """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transfer initiated / processed"),
        @ApiResponse(responseCode = "400", description = "Validation error",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials / wrong PIN",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Step-up authentication required",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Payee not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Idempotency conflict (same key, different payload)",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Daily limit exceeded or bank debit rejected",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<TransferResponse> initiateTransfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Step-Up-Pin", required = false) String stepUpPinHeader,
            @Valid @RequestBody InitiateTransferRequest request,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        User sender = getUser(userId);
        TransferResponse response = transferService.initiateTransfer(
                idempotencyKey, stepUpPinHeader, request, sender, extractIp(httpRequest));

        return ResponseEntity.ok(response);
    }

    // ── GET /transfers/{id} ────────────────────────────────────

    @Operation(summary = "Get single transfer",
               description = "Returns transfer details. Object-level check: user must be sender or receiver.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transfer details"),
        @ApiResponse(responseCode = "403", description = "Forbidden (not party to transfer)",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Transfer not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getTransfer(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {

        TransferResponse response = transferService.getTransferById(id, userId);
        return ResponseEntity.ok(response);
    }

    // ── GET /transfers/{id}/receipt ───────────────────────────

    @Operation(summary = "Get transaction receipt",
               description = "Returns complete structured receipt data for a transfer. Object-level check: caller must be sender or receiver.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transaction receipt"),
        @ApiResponse(responseCode = "403", description = "Forbidden (not party to transfer)",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Transfer not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}/receipt")
    public ResponseEntity<ReceiptResponse> getTransferReceipt(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {

        ReceiptResponse receipt = transferService.getTransferReceipt(id, userId);
        return ResponseEntity.ok(receipt);
    }

    // ── GET /transfers ─────────────────────────────────────────

    @Operation(summary = "List transfer history with filters",
               description = "Returns paginated list of user's transfers with filters by date range, direction, bank, status, and amount range.")
    @ApiResponse(responseCode = "200", description = "Paginated transfers")
    @GetMapping
    public ResponseEntity<Page<TransferResponse>> listTransfers(
            @RequestParam(required = false) TransferDirection direction,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String bank,
            @RequestParam(required = false) java.math.BigDecimal minAmount,
            @RequestParam(required = false) java.math.BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Long userId) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
        Page<TransferResponse> result = transferService.getTransfersWithFilters(
                userId, direction, status, from, to, bank, minAmount, maxAmount, pageable);
        return ResponseEntity.ok(result);
    }

    // ── POST /transfers/{id}/reconcile ─────────────────────────

    @Operation(summary = "Reconcile transfer",
               description = "Queries the bank adapter to resolve a transfer in REQUIRES_RECONCILIATION status.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reconciliation result"),
        @ApiResponse(responseCode = "404", description = "Transfer not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/reconcile")
    public ResponseEntity<ReconcileResultResponse> reconcileTransfer(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        ReconcileResultResponse result = reconciliationService.reconcileTransfer(id, userId);
        return ResponseEntity.ok(result);
    }

    // ── POST /transfers/{id}/reversal ──────────────────────────

    @Operation(summary = "Request transfer reversal",
               description = "Sender requests a reversal for a COMPLETED transfer. Transitions to REVERSAL_PENDING.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reversal requested"),
        @ApiResponse(responseCode = "403", description = "Forbidden (not sender)",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Transfer is not in COMPLETED status",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/reversal")
    public ResponseEntity<TransferResponse> requestReversal(
            @PathVariable Long id,
            @Valid @RequestBody ReversalRequest request,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        Transfer transfer = reversalService.requestReversal(id, userId, request.reason(), extractIp(httpRequest));
        return ResponseEntity.ok(TransferResponse.from(transfer));
    }

    // ── POST /transfers/{id}/challenge ─────────────────────────

    @Operation(summary = "Submit OTP challenge for card transfer",
               description = "Submits OTP to satisfy card challenge requirement and proceed with payment processing.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Challenge verified and transfer processed"),
        @ApiResponse(responseCode = "400", description = "Validation error",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden (not party to transfer)",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Transfer not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Transfer not awaiting challenge",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Challenge failed or maximum attempts exceeded",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/challenge")
    public ResponseEntity<TransferResponse> challengeTransfer(
            @PathVariable Long id,
            @Valid @RequestBody TransferChallengeRequest request,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        TransferResponse response = transferService.challengeTransfer(id, request.otp(), userId, extractIp(httpRequest));
        return ResponseEntity.ok(response);
    }

    // ── Helpers ────────────────────────────────────────────────

    private User getUser(Long userId) {
        return userStore.findUserById(userId)
                .map(com.lankapay.identity.domain.UserAccount::toUser)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found with id: " + userId));
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
