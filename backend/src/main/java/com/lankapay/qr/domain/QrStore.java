package com.lankapay.qr.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Storage port interface for QR codes and payment requests.
 * Decouples services from specific database persistence technologies.
 */
public interface QrStore {

    QrCodeRecord saveQr(QrCodeRecord qr);

    Optional<QrCodeRecord> findQrById(Long id);

    Optional<QrCodeRecord> findQrByReference(String reference);

    Optional<QrCodeRecord> findQrByQrId(String qrId);

    Optional<QrCodeRecord> findQrByReferenceOrQrId(String ref);

    Optional<QrCodeRecord> findQrByReferenceAndStatus(String reference, QrStatus status);

    List<QrCodeRecord> findQrByMerchantId(Long merchantId);

    int expireQrOlderThan(Instant now);

    PaymentRequestRecord savePaymentRequest(PaymentRequestRecord request);

    Optional<PaymentRequestRecord> findPaymentRequestById(Long id);

    Optional<PaymentRequestRecord> findPaymentRequestByReference(String reference);

    List<PaymentRequestRecord> findPaymentRequestsByRequesterId(Long requesterId);

    List<PaymentRequestRecord> findPaymentRequestsByPayerId(Long payerId);

    int expirePaymentRequestsOlderThan(Instant now);

    PaymentRequestRecord consumePaymentRequest(String reference, Long transferId);
}
