package com.lankapay.qr.store.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.qr.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Firestore implementation of {@link QrStore}.
 *
 * Requirements:
 * - qrReferences collection: document ID = opaque reference (e.g. QREF-xxx, QREF-STATIC-xxx).
 * - paymentRequests collection: single-use consumed in a transaction.
 */
@Component
public class FirestoreQrStore implements QrStore {

    private static final Logger log = LoggerFactory.getLogger(FirestoreQrStore.class);

    public static final String QR_REFERENCES_COLLECTION = "qrReferences";
    public static final String PAYMENT_REQUESTS_COLLECTION = "paymentRequests";

    private final Firestore firestore;
    private final AtomicLong fallbackIdGen = new AtomicLong(System.currentTimeMillis() % 100000000L);

    public FirestoreQrStore(Firestore firestore) {
        this.firestore = firestore;
    }

    private Long nextId() {
        return fallbackIdGen.incrementAndGet();
    }

    // ─────────────────────────────────────────────────────────────
    // QR Code References (document ID = opaque reference)
    // ─────────────────────────────────────────────────────────────

    @Override
    public QrCodeRecord saveQr(QrCodeRecord qr) {
        String ref = qr.reference();
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("QR reference cannot be blank for Firestore document ID");
        }
        Long id = qr.id() != null ? qr.id() : nextId();

        DocumentReference docRef = firestore.collection(QR_REFERENCES_COLLECTION).document(ref);

        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("merchantId", qr.merchantId());
        data.put("qrId", qr.qrId());
        data.put("reference", ref);
        data.put("qrType", qr.qrType() != null ? qr.qrType().name() : null);
        if (qr.amount() != null) {
            data.put("amount", qr.amount().doubleValue());
            data.put("amountMinor", qr.amount().multiply(BigDecimal.valueOf(100)).longValue());
        } else {
            data.put("amount", null);
            data.put("amountMinor", null);
        }
        data.put("currency", qr.currency() != null ? qr.currency() : "LKR");
        data.put("note", qr.note());
        data.put("status", qr.status() != null ? qr.status().name() : QrStatus.ACTIVE.name());
        data.put("oneTimeUse", qr.oneTimeUse());
        data.put("payload", qr.payload());
        data.put("expiresAt", qr.expiresAt() != null ? toTimestamp(qr.expiresAt()) : null);
        data.put("createdAt", qr.createdAt() != null ? toTimestamp(qr.createdAt()) : FieldValue.serverTimestamp());

        try {
            docRef.set(data).get();
            return new QrCodeRecord(
                    id,
                    qr.merchantId(),
                    qr.qrId(),
                    ref,
                    qr.qrType(),
                    qr.amount(),
                    qr.currency(),
                    qr.note(),
                    qr.status(),
                    qr.oneTimeUse(),
                    qr.payload(),
                    qr.expiresAt(),
                    qr.createdAt() != null ? qr.createdAt() : Instant.now()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while saving QR reference", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Error saving QR reference: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<QrCodeRecord> findQrById(Long id) {
        if (id == null) return Optional.empty();
        try {
            QuerySnapshot snap = firestore.collection(QR_REFERENCES_COLLECTION)
                    .whereEqualTo("id", id)
                    .limit(1)
                    .get()
                    .get();
            if (snap.isEmpty()) return Optional.empty();
            return Optional.of(mapQr(snap.getDocuments().get(0)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<QrCodeRecord> findQrByReference(String reference) {
        if (reference == null || reference.isBlank()) return Optional.empty();
        try {
            DocumentSnapshot snap = firestore.collection(QR_REFERENCES_COLLECTION).document(reference).get().get();
            if (!snap.exists()) return Optional.empty();
            return Optional.of(mapQr(snap));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<QrCodeRecord> findQrByQrId(String qrId) {
        if (qrId == null || qrId.isBlank()) return Optional.empty();
        try {
            QuerySnapshot snap = firestore.collection(QR_REFERENCES_COLLECTION)
                    .whereEqualTo("qrId", qrId)
                    .limit(1)
                    .get()
                    .get();
            if (snap.isEmpty()) return Optional.empty();
            return Optional.of(mapQr(snap.getDocuments().get(0)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<QrCodeRecord> findQrByReferenceOrQrId(String ref) {
        if (ref == null || ref.isBlank()) return Optional.empty();
        Optional<QrCodeRecord> byRef = findQrByReference(ref);
        if (byRef.isPresent()) return byRef;
        return findQrByQrId(ref);
    }

    @Override
    public Optional<QrCodeRecord> findQrByReferenceAndStatus(String reference, QrStatus status) {
        return findQrByReference(reference).filter(qr -> qr.status() == status);
    }

    @Override
    public List<QrCodeRecord> findQrByMerchantId(Long merchantId) {
        if (merchantId == null) return Collections.emptyList();
        try {
            QuerySnapshot snap = firestore.collection(QR_REFERENCES_COLLECTION)
                    .whereEqualTo("merchantId", merchantId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .get();
            return snap.getDocuments().stream().map(this::mapQr).toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int expireQrOlderThan(Instant now) {
        try {
            QuerySnapshot snap = firestore.collection(QR_REFERENCES_COLLECTION)
                    .whereEqualTo("status", QrStatus.ACTIVE.name())
                    .whereEqualTo("qrType", QrType.DYNAMIC.name())
                    .whereLessThanOrEqualTo("expiresAt", toTimestamp(now))
                    .get()
                    .get();

            WriteBatch batch = firestore.batch();
            for (DocumentSnapshot doc : snap.getDocuments()) {
                batch.update(doc.getReference(), "status", QrStatus.EXPIRED.name());
            }
            batch.commit().get();
            return snap.size();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Payment Requests (single-use consumed in a transaction)
    // ─────────────────────────────────────────────────────────────

    @Override
    public PaymentRequestRecord savePaymentRequest(PaymentRequestRecord request) {
        String ref = request.reference();
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("Payment request reference cannot be blank");
        }
        Long id = request.id() != null ? request.id() : nextId();

        DocumentReference docRef = firestore.collection(PAYMENT_REQUESTS_COLLECTION).document(ref);

        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("requesterId", request.requesterId());
        data.put("payerId", request.payerId());
        data.put("qrCodeId", request.qrCodeId());
        data.put("reference", ref);
        if (request.amount() != null) {
            data.put("amount", request.amount().doubleValue());
            data.put("amountMinor", request.amount().multiply(BigDecimal.valueOf(100)).longValue());
        }
        data.put("currency", request.currency() != null ? request.currency() : "LKR");
        data.put("note", request.note());
        data.put("status", request.status() != null ? request.status().name() : PaymentRequestStatus.PENDING.name());
        data.put("expiresAt", request.expiresAt() != null ? toTimestamp(request.expiresAt()) : null);
        data.put("fulfilledTransferId", request.fulfilledTransferId());
        data.put("createdAt", request.createdAt() != null ? toTimestamp(request.createdAt()) : FieldValue.serverTimestamp());

        try {
            docRef.set(data).get();
            return new PaymentRequestRecord(
                    id,
                    request.requesterId(),
                    request.payerId(),
                    request.qrCodeId(),
                    ref,
                    request.amount(),
                    request.currency(),
                    request.note(),
                    request.status(),
                    request.expiresAt(),
                    request.fulfilledTransferId(),
                    request.createdAt() != null ? request.createdAt() : Instant.now()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<PaymentRequestRecord> findPaymentRequestById(Long id) {
        if (id == null) return Optional.empty();
        try {
            QuerySnapshot snap = firestore.collection(PAYMENT_REQUESTS_COLLECTION)
                    .whereEqualTo("id", id)
                    .limit(1)
                    .get()
                    .get();
            if (snap.isEmpty()) return Optional.empty();
            return Optional.of(mapPaymentRequest(snap.getDocuments().get(0)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<PaymentRequestRecord> findPaymentRequestByReference(String reference) {
        if (reference == null || reference.isBlank()) return Optional.empty();
        try {
            DocumentSnapshot snap = firestore.collection(PAYMENT_REQUESTS_COLLECTION).document(reference).get().get();
            if (!snap.exists()) return Optional.empty();
            return Optional.of(mapPaymentRequest(snap));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<PaymentRequestRecord> findPaymentRequestsByRequesterId(Long requesterId) {
        if (requesterId == null) return Collections.emptyList();
        try {
            QuerySnapshot snap = firestore.collection(PAYMENT_REQUESTS_COLLECTION)
                    .whereEqualTo("requesterId", requesterId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .get();
            return snap.getDocuments().stream().map(this::mapPaymentRequest).toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<PaymentRequestRecord> findPaymentRequestsByPayerId(Long payerId) {
        if (payerId == null) return Collections.emptyList();
        try {
            QuerySnapshot snap = firestore.collection(PAYMENT_REQUESTS_COLLECTION)
                    .whereEqualTo("payerId", payerId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .get();
            return snap.getDocuments().stream().map(this::mapPaymentRequest).toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int expirePaymentRequestsOlderThan(Instant now) {
        try {
            QuerySnapshot snap = firestore.collection(PAYMENT_REQUESTS_COLLECTION)
                    .whereEqualTo("status", PaymentRequestStatus.PENDING.name())
                    .whereLessThanOrEqualTo("expiresAt", toTimestamp(now))
                    .get()
                    .get();

            WriteBatch batch = firestore.batch();
            for (DocumentSnapshot doc : snap.getDocuments()) {
                batch.update(doc.getReference(), "status", PaymentRequestStatus.EXPIRED.name());
            }
            batch.commit().get();
            return snap.size();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Single-use atomic consumption of a payment request in a Firestore transaction.
     */
    @Override
    public PaymentRequestRecord consumePaymentRequest(String reference, Long transferId) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Reference is required to consume payment request");
        }

        DocumentReference prRef = firestore.collection(PAYMENT_REQUESTS_COLLECTION).document(reference);

        try {
            return firestore.runTransaction(transaction -> {
                DocumentSnapshot snap = transaction.get(prRef).get();
                if (!snap.exists()) {
                    throw new ResourceNotFoundException("PAYMENT_REQUEST_NOT_FOUND",
                            "Payment request not found with reference: " + reference);
                }

                PaymentRequestRecord record = mapPaymentRequest(snap);

                if (record.status() == PaymentRequestStatus.COMPLETED) {
                    throw new ConflictException("PAYMENT_REQUEST_ALREADY_FULFILLED",
                            "Payment request has already been fulfilled");
                }

                if (record.isExpired()) {
                    transaction.update(prRef, "status", PaymentRequestStatus.EXPIRED.name());
                    throw new ValidationException("PAYMENT_REQUEST_EXPIRED",
                            "Payment request has expired");
                }

                // Mark payment request COMPLETED
                Map<String, Object> updates = new HashMap<>();
                updates.put("status", PaymentRequestStatus.COMPLETED.name());
                updates.put("fulfilledTransferId", transferId);
                updates.put("updatedAt", FieldValue.serverTimestamp());
                transaction.update(prRef, updates);

                // If linked to QR code, mark QR USED within the exact same transaction
                if (record.qrCodeId() != null) {
                    try {
                        QuerySnapshot qrSnap = firestore.collection(QR_REFERENCES_COLLECTION)
                                .whereEqualTo("id", record.qrCodeId())
                                .limit(1)
                                .get()
                                .get();
                        if (!qrSnap.isEmpty()) {
                            DocumentReference qrRef = qrSnap.getDocuments().get(0).getReference();
                            transaction.update(qrRef, "status", QrStatus.USED.name());
                        }
                    } catch (Exception ex) {
                        log.warn("Could not mark linked QR code id={} as used: {}", record.qrCodeId(), ex.getMessage());
                    }
                }

                return record.markCompleted(transferId);
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ResourceNotFoundException rne) throw rne;
            if (cause instanceof ConflictException ce) throw ce;
            if (cause instanceof ValidationException ve) {
                if ("PAYMENT_REQUEST_EXPIRED".equals(ve.getErrorCode())) {
                    try {
                        prRef.update("status", PaymentRequestStatus.EXPIRED.name()).get();
                    } catch (Exception ignored) {
                    }
                }
                throw ve;
            }
            throw new RuntimeException("Transaction error consuming payment request: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Mapping Helpers
    // ─────────────────────────────────────────────────────────────

    private QrCodeRecord mapQr(DocumentSnapshot doc) {
        Long id = doc.getLong("id");
        Long merchantId = doc.getLong("merchantId");
        String qrId = doc.getString("qrId");
        String ref = doc.getString("reference");
        if (ref == null) ref = doc.getId();

        String qrTypeStr = doc.getString("qrType");
        QrType qrType = qrTypeStr != null ? QrType.valueOf(qrTypeStr) : null;

        BigDecimal amount = null;
        if (doc.contains("amountMinor") && doc.get("amountMinor") != null) {
            Long minor = doc.getLong("amountMinor");
            amount = BigDecimal.valueOf(minor).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else if (doc.contains("amount") && doc.get("amount") != null) {
            amount = BigDecimal.valueOf(doc.getDouble("amount")).setScale(2, RoundingMode.HALF_UP);
        }

        String currency = doc.getString("currency");
        String note = doc.getString("note");
        String statusStr = doc.getString("status");
        QrStatus status = statusStr != null ? QrStatus.valueOf(statusStr) : QrStatus.ACTIVE;

        Boolean oneTimeUse = doc.getBoolean("oneTimeUse");
        String payload = doc.getString("payload");
        Instant expiresAt = toInstant(doc.getTimestamp("expiresAt"));
        Instant createdAt = toInstant(doc.getTimestamp("createdAt"));

        return new QrCodeRecord(
                id, merchantId, qrId, ref, qrType, amount, currency, note,
                status, Boolean.TRUE.equals(oneTimeUse), payload, expiresAt, createdAt
        );
    }

    private PaymentRequestRecord mapPaymentRequest(DocumentSnapshot doc) {
        Long id = doc.getLong("id");
        Long requesterId = doc.getLong("requesterId");
        Long payerId = doc.getLong("payerId");
        Long qrCodeId = doc.getLong("qrCodeId");
        String ref = doc.getString("reference");
        if (ref == null) ref = doc.getId();

        BigDecimal amount = null;
        if (doc.contains("amountMinor") && doc.get("amountMinor") != null) {
            Long minor = doc.getLong("amountMinor");
            amount = BigDecimal.valueOf(minor).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else if (doc.contains("amount") && doc.get("amount") != null) {
            amount = BigDecimal.valueOf(doc.getDouble("amount")).setScale(2, RoundingMode.HALF_UP);
        }

        String currency = doc.getString("currency");
        String note = doc.getString("note");
        String statusStr = doc.getString("status");
        PaymentRequestStatus status = statusStr != null ? PaymentRequestStatus.valueOf(statusStr) : PaymentRequestStatus.PENDING;

        Instant expiresAt = toInstant(doc.getTimestamp("expiresAt"));
        Long fulfilledTransferId = doc.getLong("fulfilledTransferId");
        Instant createdAt = toInstant(doc.getTimestamp("createdAt"));

        return new PaymentRequestRecord(
                id, requesterId, payerId, qrCodeId, ref, amount, currency, note,
                status, expiresAt, fulfilledTransferId, createdAt
        );
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano()) : null;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()) : null;
    }
}
