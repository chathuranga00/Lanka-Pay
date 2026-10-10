package com.lankapay.qr.service;

import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.User;
import com.lankapay.qr.domain.QrCode;
import com.lankapay.qr.domain.QrStatus;
import com.lankapay.qr.domain.QrType;
import com.lankapay.qr.repository.QrCodeRepository;
import com.lankapay.qr.web.dto.CreateQrReferenceRequest;
import com.lankapay.qr.web.dto.QrReferenceResponse;
import com.lankapay.qr.web.dto.ResolvedQrRecipientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Service managing opaque QR references (static and dynamic) per section 10.
 *
 * Invariants:
 * - Opaque references: no account numbers, no bank codes, no PII, no auth tokens in QR.
 * - Static QR: reusable indefinitely (oneTimeUse = false), no fixed amount or expiry.
 * - Dynamic QR: amount + expiry + unique reference, one-time use.
 * - Scanning only RESOLVES the recipient; funds are never moved upon scanning.
 */
@Service
public class QrService {

    private static final Logger log = LoggerFactory.getLogger(QrService.class);

    private final QrCodeRepository qrCodeRepository;

    public QrService(QrCodeRepository qrCodeRepository) {
        this.qrCodeRepository = qrCodeRepository;
    }

    /**
     * Create an opaque QR reference (static or dynamic).
     */
    @Transactional
    public QrReferenceResponse createQrReference(CreateQrReferenceRequest request, User merchant) {
        QrType type = request.resolvedType();
        QrCode qr;

        if (type == QrType.STATIC) {
            qr = QrCode.createStatic(merchant, request.currency(), request.note());
            log.info("Created static QR reference='{}' for user id={}", qr.getReference(), merchant.getId());
        } else {
            if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("INVALID_AMOUNT", "Amount is required and must be > 0 for dynamic QR codes");
            }
            Instant expiresAt = Instant.now().plus(request.resolvedValidityMinutes(), ChronoUnit.MINUTES);
            qr = QrCode.createDynamic(merchant, request.amount(), request.currency(), request.note(), expiresAt);
            log.info("Created dynamic QR reference='{}' for user id={}, amount={}, expiresAt={}",
                    qr.getReference(), merchant.getId(), request.amount(), expiresAt);
        }

        qr = qrCodeRepository.save(qr);
        return QrReferenceResponse.from(qr);
    }

    /**
     * Scan / resolve an opaque QR reference.
     *
     * IMPORTANT: A scan only RESOLVES the recipient profile and parameters.
     * It does NOT perform or complete a financial transfer.
     *
     * @param reference The opaque reference scanned from the QR code
     * @return Resolved recipient information for transfer confirmation
     * @throws ResourceNotFoundException if reference does not exist
     * @throws ValidationException       if expired or cancelled
     * @throws ConflictException         if dynamic QR was already used
     */
    @Transactional
    public ResolvedQrRecipientResponse resolveQrReference(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new ValidationException("INVALID_REFERENCE", "QR reference cannot be blank");
        }

        QrCode qr = qrCodeRepository.findByReferenceOrQrId(reference)
                .orElseThrow(() -> new ResourceNotFoundException("QR_NOT_FOUND",
                        "QR reference is invalid or unknown: " + reference));

        // Enforce lifecycle and status checks
        if (qr.getStatus() == QrStatus.CANCELLED) {
            log.warn("Scanned cancelled QR reference='{}'", reference);
            throw new ValidationException("QR_CANCELLED", "This QR code has been cancelled");
        }

        if (qr.getStatus() == QrStatus.USED) {
            log.warn("Scanned already-used dynamic QR reference='{}'", reference);
            throw new ConflictException("QR_ALREADY_USED", "This one-time QR code has already been used");
        }

        if (qr.isExpired()) {
            log.warn("Scanned expired dynamic QR reference='{}', expiredAt={}", reference, qr.getExpiresAt());
            qr.markExpired();
            qrCodeRepository.save(qr);
            throw new ValidationException("QR_EXPIRED", "This QR code has expired");
        }

        log.info("Resolved QR reference='{}': type={}, recipientId={}, amount={}",
                reference, qr.getQrType(), qr.getMerchant().getId(), qr.getAmount());

        return ResolvedQrRecipientResponse.from(qr);
    }

    /**
     * Mark a dynamic one-time QR reference as USED when a payment completes.
     */
    @Transactional
    public void markQrUsed(String reference) {
        qrCodeRepository.findByReferenceOrQrId(reference).ifPresent(qr -> {
            if (qr.isOneTimeUse()) {
                qr.markUsed();
                qrCodeRepository.save(qr);
                log.info("Marked dynamic QR reference='{}' as USED", reference);
            }
        });
    }
}
