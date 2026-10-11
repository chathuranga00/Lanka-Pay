package com.lankapay.bank.web;

import com.lankapay.bank.domain.BankAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.bank.service.BankAccountService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Bank account management endpoints.
 *
 * All endpoints require a valid JWT.
 * Object-level authorization is enforced in {@link BankAccountService}:
 * attempting to access another user's account returns 403.
 */
@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Bank Accounts", description = "Link, list, update, and remove bank accounts")
public class BankAccountController {

    private final BankAccountService  bankAccountService;
    private final LinkedAccountStore  linkedAccountStore;

    public BankAccountController(BankAccountService bankAccountService,
                                  LinkedAccountStore linkedAccountStore) {
        this.bankAccountService  = bankAccountService;
        this.linkedAccountStore  = linkedAccountStore;
    }

    // ── GET /bank-institutions ─────────────────────────────────

    @Operation(summary = "List available bank institutions",
               description = "Returns all active institutions a user can link accounts from. "
                             + "All institutions are SIMULATED in this prototype.")
    @ApiResponse(responseCode = "200", description = "List of institutions")
    @GetMapping("/bank-institutions")
    public ResponseEntity<List<BankInstitutionResponse>> listInstitutions() {
        List<BankInstitutionResponse> list = linkedAccountStore.findAllInstitutions().stream()
                .filter(i -> i.active())
                .map(BankInstitutionResponse::from)
                .toList();
        return ResponseEntity.ok(list);
    }

    // ── POST /bank-accounts/link ───────────────────────────────

    @Operation(summary = "Link a new bank account",
               description = """
                   Verifies account ownership with the bank (SIMULATED), records consent,
                   encrypts the account reference at rest, and persists the account.
                   Requires consentGiven=true in the request body.
                   """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account linked successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error or ownership failed",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Account already linked",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/bank-accounts/link")
    public ResponseEntity<BankAccountResponse> linkAccount(
            @Valid @RequestBody LinkBankAccountRequest request,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        BankAccount account = bankAccountService.linkAccount(
                userId,
                request.institutionCode(),
                request.accountNumber(),
                request.accountName(),
                request.label(),
                Boolean.TRUE.equals(request.consentGiven()),
                extractIp(httpRequest));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BankAccountResponse.from(account, true));
    }

    // ── GET /bank-accounts ─────────────────────────────────────

    @Operation(summary = "List linked bank accounts",
               description = "Returns all bank accounts (any status) linked by the authenticated user.")
    @ApiResponse(responseCode = "200", description = "List of accounts")
    @GetMapping("/bank-accounts")
    public ResponseEntity<List<BankAccountResponse>> listAccounts(
            @AuthenticationPrincipal Long userId) {

        List<BankAccountResponse> list = bankAccountService.listAccounts(userId).stream()
                .map(a -> BankAccountResponse.from(a, true))
                .toList();
        return ResponseEntity.ok(list);
    }

    // ── GET /bank-accounts/{id} ────────────────────────────────

    @Operation(summary = "Get a single linked bank account")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account details"),
        @ApiResponse(responseCode = "403", description = "Not the owner",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/bank-accounts/{id}")
    public ResponseEntity<BankAccountResponse> getAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {

        BankAccount account = bankAccountService.getAccount(id, userId);
        return ResponseEntity.ok(BankAccountResponse.from(account, true));
    }

    // ── PATCH /bank-accounts/{id} ──────────────────────────────

    @Operation(summary = "Update bank account metadata",
               description = "Update label, discoverable, or receivingEnabled. "
                             + "Only non-null fields are applied.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Updated account"),
        @ApiResponse(responseCode = "400", description = "Validation error",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Not the owner",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/bank-accounts/{id}")
    public ResponseEntity<BankAccountResponse> patchAccount(
            @PathVariable Long id,
            @Valid @RequestBody PatchBankAccountRequest request,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        BankAccount account = bankAccountService.patchAccount(
                id, userId,
                request.label(),
                request.discoverable(),
                request.receivingEnabled(),
                extractIp(httpRequest));

        return ResponseEntity.ok(BankAccountResponse.from(account, true));
    }

    // ── POST /bank-accounts/{id}/set-default ──────────────────

    @Operation(summary = "Set as default payment account",
               description = "Makes this account the default for outgoing payments. "
                             + "Atomically clears the default flag on all other accounts.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Default account updated"),
        @ApiResponse(responseCode = "403", description = "Not the owner",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/bank-accounts/{id}/set-default")
    public ResponseEntity<BankAccountResponse> setDefault(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        BankAccount account = bankAccountService.setDefault(id, userId, extractIp(httpRequest));
        return ResponseEntity.ok(BankAccountResponse.from(account, true));
    }

    // ── DELETE /bank-accounts/{id} ────────────────────────────

    @Operation(summary = "Remove a bank account",
               description = """
                   Soft-deletes the account (status → REMOVED). Pending transactions are
                   preserved. The account can no longer send or receive transfers.
                   Requires consentGiven=true in the request body.
                   """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Account removed"),
        @ApiResponse(responseCode = "400", description = "Consent not given",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Not the owner",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Already removed",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/bank-accounts/{id}")
    public ResponseEntity<Void> removeAccount(
            @PathVariable Long id,
            @Valid @RequestBody RemoveBankAccountRequest request,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        bankAccountService.removeAccount(
                id, userId,
                Boolean.TRUE.equals(request.consentGiven()),
                extractIp(httpRequest));

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
