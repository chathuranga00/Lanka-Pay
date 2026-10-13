package com.lankapay.bill.web;

import com.lankapay.bill.adapter.BillerCategory;
import com.lankapay.bill.adapter.BillerInfo;
import com.lankapay.bill.service.BillService;
import com.lankapay.bill.web.dto.*;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Bills", description = "Bill inquiries, bill payments, reconciliation, and payment receipts")
public class BillController {

    private final BillService billService;
    private final UserStore userStore;

    public BillController(BillService billService, UserStore userStore) {
        this.billService = billService;
        this.userStore = userStore;
    }

    @GetMapping({"/api/v1/billers", "/api/v1/bills/billers"})
    @Operation(summary = "List supported billers")
    public ResponseEntity<List<BillerInfo>> listBillers(
            @RequestParam(required = false) BillerCategory category,
            @RequestParam(required = false) String query
    ) {
        return ResponseEntity.ok(billService.listBillers(category, query));
    }

    @GetMapping("/api/v1/billers/{id}")
    @Operation(summary = "Get biller by ID")
    public ResponseEntity<BillerInfo> getBiller(@PathVariable String id) {
        return ResponseEntity.ok(billService.getBiller(id));
    }

    @PostMapping("/api/v1/bills/lookup")
    @Operation(summary = "Lookup bill details by customer reference")
    public ResponseEntity<BillLookupResponse> lookupBill(@Valid @RequestBody BillLookupRequest request) {
        return ResponseEntity.ok(billService.lookupBill(request.billerId(), request.customerReference()));
    }

    @PostMapping("/api/v1/bills/pay")
    @Operation(summary = "Execute bill payment with step-up authentication and idempotency")
    public ResponseEntity<BillPaymentReceiptResponse> payBill(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Step-Up-Pin", required = false) String stepUpPinHeader,
            @Valid @RequestBody PayBillRequest request,
            HttpServletRequest httpRequest
    ) {
        User user = resolveUser(principal);
        String clientIp = httpRequest.getRemoteAddr();
        BillPaymentReceiptResponse receipt = billService.payBill(user, request, idempotencyKey, stepUpPinHeader, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
    }

    @GetMapping("/api/v1/bills/payments")
    @Operation(summary = "List user bill payments")
    public ResponseEntity<List<BillPaymentReceiptResponse>> listPayments(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit
    ) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(billService.listPayments(user.getId(), offset, limit));
    }

    @GetMapping({"/api/v1/bills/payments/{id}", "/api/v1/bills/payments/{id}/receipt", "/bills/payments/{id}/receipt"})
    @Operation(summary = "Get specific bill payment receipt")
    public ResponseEntity<BillPaymentReceiptResponse> getPayment(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @PathVariable Long id
    ) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(billService.getPaymentById(id, user.getId()));
    }

    @PostMapping("/api/v1/bills/payments/{id}/reconcile")
    @Operation(summary = "Reconcile payment in REQUIRES_RECONCILIATION status")
    public ResponseEntity<BillPaymentReceiptResponse> reconcilePayment(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @PathVariable Long id
    ) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(billService.reconcilePayment(id, user.getId()));
    }

    private User resolveUser(org.springframework.security.core.userdetails.User principal) {
        return userStore.findUserByPhone(principal.getUsername())
                .map(acc -> {
                    User u = new User(acc.phone(), acc.fullName(), null, null);
                    u.setId(acc.id());
                    return u;
                })
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + principal.getUsername()));
    }
}
