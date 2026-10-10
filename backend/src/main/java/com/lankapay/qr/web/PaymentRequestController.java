package com.lankapay.qr.web;

import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.web.ErrorResponse;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.repository.UserRepository;
import com.lankapay.qr.service.PaymentRequestService;
import com.lankapay.qr.web.dto.CreatePaymentRequestDto;
import com.lankapay.qr.web.dto.PaymentRequestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    private final UserRepository userRepository;

    public PaymentRequestController(PaymentRequestService paymentRequestService,
                                    UserRepository userRepository) {
        this.paymentRequestService = paymentRequestService;
        this.userRepository = userRepository;
    }

    // ── POST /payment-requests ─────────────────────────────────

    @Operation(summary = "Create a payment request",
               description = "Creates a payment request with an opaque reference and linked dynamic QR code.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Payment request created"),
        @ApiResponse(responseCode = "400", description = "Validation error",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Target payer not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<PaymentRequestResponse> createPaymentRequest(
            @Valid @RequestBody CreatePaymentRequestDto request,
            @AuthenticationPrincipal Long userId) {

        User user = getUser(userId);
        PaymentRequestResponse response = paymentRequestService.createPaymentRequest(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── GET /payment-requests/{id} ─────────────────────────────

    @Operation(summary = "Get payment request details",
               description = "Object-level security: only the requester or designated payer can view the request.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Payment request details"),
        @ApiResponse(responseCode = "403", description = "Forbidden (not party to this request)",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Payment request not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<PaymentRequestResponse> getPaymentRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {

        PaymentRequestResponse response = paymentRequestService.getPaymentRequest(id, userId);
        return ResponseEntity.ok(response);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found with id: " + userId));
    }
}
