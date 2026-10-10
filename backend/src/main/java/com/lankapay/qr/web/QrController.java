package com.lankapay.qr.web;

import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.web.ErrorResponse;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.repository.UserRepository;
import com.lankapay.qr.service.QrService;
import com.lankapay.qr.web.dto.CreateQrReferenceRequest;
import com.lankapay.qr.web.dto.QrReferenceResponse;
import com.lankapay.qr.web.dto.ResolvedQrRecipientResponse;
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
@RequestMapping("/api/v1/qr")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "QR Payments", description = "Generate and resolve opaque QR code references")
public class QrController {

    private final QrService qrService;
    private final UserRepository userRepository;

    public QrController(QrService qrService, UserRepository userRepository) {
        this.qrService = qrService;
        this.userRepository = userRepository;
    }

    // ── POST /qr/references ────────────────────────────────────

    @Operation(summary = "Generate an opaque QR reference",
               description = """
                   Creates an opaque, unguessable QR reference.
                   Supports:
                   - STATIC QR: Reusable profile/merchant QR without fixed amount or expiry.
                   - DYNAMIC QR: Preset amount + expiry + unique one-time reference.
                   No account numbers, PII, or auth tokens are embedded in the QR payload.
                   """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "QR reference created successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/references")
    public ResponseEntity<QrReferenceResponse> createQrReference(
            @Valid @RequestBody CreateQrReferenceRequest request,
            @AuthenticationPrincipal Long userId) {

        User user = getUser(userId);
        QrReferenceResponse response = qrService.createQrReference(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── GET /qr/references/{reference} ─────────────────────────

    @Operation(summary = "Scan and resolve QR reference",
               description = """
                   Resolves the recipient profile and payment parameters from an opaque QR reference.
                   IMPORTANT: A scan only RESOLVES the recipient. It does not perform the transfer.
                   The payer must submit confirmation and authorization via the normal transfer flow.
                   """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recipient resolved successfully"),
        @ApiResponse(responseCode = "400", description = "QR code expired or cancelled",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "QR reference not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "One-time QR code already used",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/references/{reference}")
    public ResponseEntity<ResolvedQrRecipientResponse> resolveQrReference(
            @PathVariable String reference) {

        ResolvedQrRecipientResponse response = qrService.resolveQrReference(reference);
        return ResponseEntity.ok(response);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found with id: " + userId));
    }
}
