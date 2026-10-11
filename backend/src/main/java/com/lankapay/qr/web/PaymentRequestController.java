package com.lankapay.qr.web;

import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.qr.service.PaymentRequestService;
import com.lankapay.qr.web.dto.CreatePaymentRequestDto;
import com.lankapay.qr.web.dto.PaymentRequestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payment-requests")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Payment Requests", description = "Create and inspect peer-to-peer and merchant payment requests")
public class PaymentRequestController {

    private final PaymentRequestService paymentRequestService;
    private final UserStore userStore;

    public PaymentRequestController(PaymentRequestService paymentRequestService,
                                    UserStore userStore) {
        this.paymentRequestService = paymentRequestService;
        this.userStore = userStore;
    }

    // ── POST /payment-requests ─────────────────────────────────

    @Operation(summary = "Create a payment request")
    @PostMapping
    public ResponseEntity<PaymentRequestResponse> createPaymentRequest(
            @Valid @RequestBody CreatePaymentRequestDto dto,
            @AuthenticationPrincipal Long userId) {

        User user = getUser(userId);
        PaymentRequestResponse response = paymentRequestService.createPaymentRequest(dto, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── GET /payment-requests/{id} ─────────────────────────────

    @Operation(summary = "Get payment request details by ID")
    @GetMapping("/{id}")
    public ResponseEntity<PaymentRequestResponse> getPaymentRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {

        PaymentRequestResponse response = paymentRequestService.getPaymentRequest(id, userId);
        return ResponseEntity.ok(response);
    }

    private User getUser(Long userId) {
        UserAccount acc = userStore.findUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found with id: " + userId));
        User u = new User(acc.phone(), acc.fullName(), acc.nicNumber(), acc.passwordHash());
        u.setId(acc.id());
        u.setRole(acc.role());
        u.setStatus(acc.status());
        return u;
    }
}
