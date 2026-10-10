package com.lankapay.qr.service;

import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.repository.UserRepository;
import com.lankapay.qr.domain.PaymentRequest;
import com.lankapay.qr.domain.PaymentRequestStatus;
import com.lankapay.qr.domain.QrCode;
import com.lankapay.qr.repository.PaymentRequestRepository;
import com.lankapay.qr.repository.QrCodeRepository;
import com.lankapay.qr.web.dto.CreatePaymentRequestDto;
import com.lankapay.qr.web.dto.PaymentRequestResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Service managing peer-to-peer / merchant payment requests per section 10.
 *
 * Rules:
 * - Opaque references.
 * - Object-level authorization: only the requester or designated payer can access details.
 * - Expiry enforcement: expired payment requests cannot be fulfilled.
 */
@Service
public class PaymentRequestService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestService.class);

    private final PaymentRequestRepository paymentRequestRepository;
    private final QrCodeRepository qrCodeRepository;
    private final UserRepository userRepository;

    public PaymentRequestService(PaymentRequestRepository paymentRequestRepository,
                                 QrCodeRepository qrCodeRepository,
                                 UserRepository userRepository) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.qrCodeRepository = qrCodeRepository;
        this.userRepository = userRepository;
    }

    /**
     * Create a payment request with an unguessable reference and linked dynamic QR.
     */
    @Transactional
    public PaymentRequestResponse createPaymentRequest(CreatePaymentRequestDto dto, User requester) {
        if (dto.amount() == null || dto.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("INVALID_AMOUNT", "Payment request amount must be greater than zero");
        }

        User payer = null;
        if (dto.payerId() != null) {
            payer = userRepository.findById(dto.payerId())
                    .orElseThrow(() -> new ResourceNotFoundException("PAYER_NOT_FOUND",
                            "Target payer not found with id: " + dto.payerId()));
        } else if (dto.payerPhone() != null && !dto.payerPhone().isBlank()) {
            payer = userRepository.findByPhone(dto.payerPhone())
                    .orElseThrow(() -> new ResourceNotFoundException("PAYER_NOT_FOUND",
                            "Target payer not found with phone: " + dto.payerPhone()));
        }

        if (payer != null && payer.getId().equals(requester.getId())) {
            throw new ValidationException("CANNOT_REQUEST_FROM_SELF", "Requester and payer cannot be the same user");
        }

        Instant expiresAt = Instant.now().plus(dto.resolvedValidityMinutes(), ChronoUnit.MINUTES);

        // Generate linked dynamic QR code for scan-and-pay
        QrCode qrCode = QrCode.createDynamic(
                requester, dto.amount(), dto.currency(), dto.note(), expiresAt);
        qrCode = qrCodeRepository.save(qrCode);

        PaymentRequest paymentRequest = new PaymentRequest(
                requester, payer, dto.amount(), dto.currency(), dto.note(), expiresAt, qrCode);

        paymentRequest = paymentRequestRepository.save(paymentRequest);

        log.info("Created payment request id={}, reference='{}', requesterId={}, amount={}",
                paymentRequest.getId(), paymentRequest.getReference(), requester.getId(), dto.amount());

        return PaymentRequestResponse.from(paymentRequest);
    }

    /**
     * Get payment request by ID with object-level authorization.
     *
     * @param id                  Payment request ID
     * @param authenticatedUserId Currently logged-in user ID
     * @return PaymentRequestResponse
     * @throws AuthorizationException if the user is neither requester nor designated payer
     */
    @Transactional
    public PaymentRequestResponse getPaymentRequest(Long id, Long authenticatedUserId) {
        PaymentRequest pr = paymentRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PAYMENT_REQUEST_NOT_FOUND",
                        "Payment request not found with id: " + id));

        boolean isRequester = pr.getRequester().getId().equals(authenticatedUserId);
        boolean isDesignatedPayer = pr.getPayer() != null && pr.getPayer().getId().equals(authenticatedUserId);

        // Object-level authorization check: third party unauthorized
        if (!isRequester && !isDesignatedPayer) {
            log.warn("Unauthorized access attempt to payment request id={} by user id={}", id, authenticatedUserId);
            throw new AuthorizationException("FORBIDDEN",
                    "You do not have authorization to view this payment request");
        }

        // Enforce expiration
        if (pr.getStatus() == PaymentRequestStatus.PENDING && pr.isExpired()) {
            pr.isPayable(); // triggers status update to EXPIRED
            pr = paymentRequestRepository.save(pr);
            log.info("Payment request id={} marked as EXPIRED", id);
        }

        return PaymentRequestResponse.from(pr);
    }
}
