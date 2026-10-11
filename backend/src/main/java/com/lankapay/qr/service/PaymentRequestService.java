package com.lankapay.qr.service;

import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.qr.domain.PaymentRequestRecord;
import com.lankapay.qr.domain.PaymentRequestStatus;
import com.lankapay.qr.domain.QrCode;
import com.lankapay.qr.domain.QrCodeRecord;
import com.lankapay.qr.domain.QrStore;
import com.lankapay.qr.web.dto.CreatePaymentRequestDto;
import com.lankapay.qr.web.dto.PaymentRequestResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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

    private final QrStore qrStore;
    private final UserStore userStore;

    public PaymentRequestService(QrStore qrStore, UserStore userStore) {
        this.qrStore = qrStore;
        this.userStore = userStore;
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
            var uOpt = userStore.findUserById(dto.payerId())
                    .orElseThrow(() -> new ResourceNotFoundException("PAYER_NOT_FOUND",
                            "Target payer not found with id: " + dto.payerId()));
            payer = new User(uOpt.phone(), uOpt.fullName(), uOpt.nicNumber(), uOpt.passwordHash());
            payer.setId(uOpt.id());
        } else if (dto.payerPhone() != null && !dto.payerPhone().isBlank()) {
            var uOpt = userStore.findUserByPhone(dto.payerPhone())
                    .orElseThrow(() -> new ResourceNotFoundException("PAYER_NOT_FOUND",
                            "Target payer not found with phone: " + dto.payerPhone()));
            payer = new User(uOpt.phone(), uOpt.fullName(), uOpt.nicNumber(), uOpt.passwordHash());
            payer.setId(uOpt.id());
        }

        if (payer != null && payer.getId().equals(requester.getId())) {
            throw new ValidationException("CANNOT_REQUEST_FROM_SELF", "Requester and payer cannot be the same user");
        }

        Instant expiresAt = Instant.now().plus(dto.resolvedValidityMinutes(), ChronoUnit.MINUTES);

        QrCode tempQr = QrCode.createDynamic(
                requester, dto.amount(), dto.currency(), dto.note(), expiresAt);
        QrCodeRecord qrRecord = new QrCodeRecord(
                null,
                requester.getId(),
                tempQr.getQrId(),
                tempQr.getReference(),
                tempQr.getQrType(),
                tempQr.getAmount(),
                tempQr.getCurrency(),
                tempQr.getNote(),
                tempQr.getStatus(),
                tempQr.isOneTimeUse(),
                tempQr.getPayload(),
                tempQr.getExpiresAt(),
                tempQr.getCreatedAt()
        );
        qrRecord = qrStore.saveQr(qrRecord);

        String prRef = "PREQ-" + UUID.randomUUID();
        PaymentRequestRecord prRecord = new PaymentRequestRecord(
                null,
                requester.getId(),
                payer != null ? payer.getId() : null,
                qrRecord.id(),
                prRef,
                dto.amount(),
                dto.currency() != null ? dto.currency() : "LKR",
                dto.note(),
                PaymentRequestStatus.PENDING,
                expiresAt,
                null,
                Instant.now()
        );
        prRecord = qrStore.savePaymentRequest(prRecord);

        log.info("Created payment request id={}, reference='{}', requesterId={}, amount={}",
                prRecord.id(), prRecord.reference(), requester.getId(), dto.amount());

        return PaymentRequestResponse.from(
                prRecord,
                requester.getFullName(),
                payer != null ? payer.getFullName() : null,
                qrRecord.reference(),
                qrRecord.payload()
        );
    }

    /**
     * Get payment request by ID with object-level authorization.
     */
    @Transactional
    public PaymentRequestResponse getPaymentRequest(Long id, Long authenticatedUserId) {
        PaymentRequestRecord pr = qrStore.findPaymentRequestById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PAYMENT_REQUEST_NOT_FOUND",
                        "Payment request not found with id: " + id));

        boolean isRequester = pr.requesterId().equals(authenticatedUserId);
        boolean isDesignatedPayer = pr.payerId() != null && pr.payerId().equals(authenticatedUserId);

        if (!isRequester && !isDesignatedPayer) {
            log.warn("Unauthorized access attempt to payment request id={} by user id={}", id, authenticatedUserId);
            throw new AuthorizationException("FORBIDDEN",
                    "You do not have authorization to view this payment request");
        }

        if (pr.status() == PaymentRequestStatus.PENDING && pr.isExpired()) {
            pr = new PaymentRequestRecord(
                    pr.id(), pr.requesterId(), pr.payerId(), pr.qrCodeId(), pr.reference(),
                    pr.amount(), pr.currency(), pr.note(), PaymentRequestStatus.EXPIRED,
                    pr.expiresAt(), pr.fulfilledTransferId(), pr.createdAt()
            );
            pr = qrStore.savePaymentRequest(pr);
            log.info("Payment request id={} marked as EXPIRED", id);
        }

        String requesterName = "Requester";
        String payerName = null;
        if (userStore != null) {
            var rOpt = userStore.findUserById(pr.requesterId());
            if (rOpt.isPresent()) requesterName = rOpt.get().fullName();
            if (pr.payerId() != null) {
                var pOpt = userStore.findUserById(pr.payerId());
                if (pOpt.isPresent()) payerName = pOpt.get().fullName();
            }
        }

        String qrRef = null;
        String qrPayload = null;
        if (pr.qrCodeId() != null) {
            var qrOpt = qrStore.findQrById(pr.qrCodeId());
            if (qrOpt.isPresent()) {
                qrRef = qrOpt.get().reference();
                qrPayload = qrOpt.get().payload();
            }
        }

        return PaymentRequestResponse.from(pr, requesterName, payerName, qrRef, qrPayload);
    }
}
