package com.lankapay.transfer.store.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.firestore.FirestoreTransactionHelper;
import com.lankapay.transfer.domain.*;
import com.lankapay.transfer.web.dto.TransferDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Production Firestore store for transactions, append-only events, idempotency keys, and daily limits.
 *
 * Firestore Data Model:
 * - /transactions/{id}                         : Root transfer documents
 * - /transactions/{id}/events/{eventId}        : Append-only state transition audit trail
 * - /idempotencyKeys/{idempotencyKey}          : Idempotency keys (docId = idempotencyKey, payloadHash, txId)
 * - /dailyLimits/{userId_date}                 : User daily spend tracking
 *
 * Concurrency & Invariants:
 * - Transaction creation & idempotency key creation happen in ONE atomic Firestore transaction.
 * - Same key + same payload replays cached result; different payload is rejected with 409 Conflict.
 * - Status changes and event appends happen in ONE transaction, enforced by TransactionStateMachine.
 * - Version field provides optimistic locking against concurrent status modifications.
 * - Money is stored as long minor units (amountMinor) + currency.
 */
@Component
public class FirestoreTransactionStore implements TransactionStore {

    private static final Logger log = LoggerFactory.getLogger(FirestoreTransactionStore.class);

    public static final String TRANSACTIONS_COLLECTION = "transactions";
    public static final String EVENTS_SUBCOLLECTION = "events";
    public static final String IDEMPOTENCY_COLLECTION = "idempotencyKeys";
    public static final String DAILY_LIMITS_COLLECTION = "dailyLimits";

    private final Firestore firestore;
    private final FirestoreTransactionHelper txHelper;
    private final AtomicLong idGen = new AtomicLong(System.currentTimeMillis() % 100000000L);
    private final AtomicLong eventIdGen = new AtomicLong(System.currentTimeMillis() % 100000000L);

    public FirestoreTransactionStore(Firestore firestore) {
        this.firestore = firestore;
        this.txHelper = new FirestoreTransactionHelper(firestore);
    }

    private Long nextTransferId() {
        return System.currentTimeMillis() * 1000L + (idGen.incrementAndGet() % 1000L);
    }

    private Long nextEventId() {
        return System.currentTimeMillis() * 1000L + (eventIdGen.incrementAndGet() % 1000L);
    }

    // ── Transfers ──────────────────────────────────────────

    @Override
    public Optional<TransactionRecord> findTransferById(Long id) {
        if (id == null) return Optional.empty();
        try {
            DocumentSnapshot snap = firestore.collection(TRANSACTIONS_COLLECTION)
                    .document(String.valueOf(id)).get().get();
            if (!snap.exists()) return Optional.empty();
            return Optional.of(mapTransaction(snap));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<TransactionRecord> findTransferByReference(String reference) {
        if (reference == null || reference.isBlank()) return Optional.empty();
        try {
            QuerySnapshot snap = firestore.collection(TRANSACTIONS_COLLECTION)
                    .whereEqualTo("reference", reference)
                    .limit(1)
                    .get().get();
            if (snap.isEmpty()) return Optional.empty();
            return Optional.of(mapTransaction(snap.getDocuments().get(0)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<TransactionRecord> findTransferByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return Optional.empty();
        try {
            DocumentSnapshot idemSnap = firestore.collection(IDEMPOTENCY_COLLECTION)
                    .document(idempotencyKey).get().get();
            if (idemSnap.exists()) {
                Long txId = idemSnap.getLong("txId");
                if (txId == null) txId = idemSnap.getLong("transferId");
                if (txId != null) {
                    return findTransferById(txId);
                }
            }
            QuerySnapshot snap = firestore.collection(TRANSACTIONS_COLLECTION)
                    .whereEqualTo("idempotencyKey", idempotencyKey)
                    .limit(1)
                    .get().get();
            if (snap.isEmpty()) return Optional.empty();
            return Optional.of(mapTransaction(snap.getDocuments().get(0)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean existsTransferByReference(String reference) {
        return findTransferByReference(reference).isPresent();
    }

    @Override
    public boolean existsTransferByIdempotencyKey(String idempotencyKey) {
        return findTransferByIdempotencyKey(idempotencyKey).isPresent();
    }

    @Override
    public TransactionRecord saveTransfer(TransactionRecord transfer) {
        if (transfer.id() == null) {
            Long transferId = nextTransferId();
            DocumentReference txRef = firestore.collection(TRANSACTIONS_COLLECTION).document(String.valueOf(transferId));

            long amountMinor = transfer.amount() != null
                    ? transfer.amount().movePointRight(2).longValue()
                    : 0L;

            Map<String, Object> data = new HashMap<>();
            data.put("id", transferId);
            data.put("reference", transfer.reference());
            data.put("idempotencyKey", transfer.idempotencyKey());
            data.put("senderId", transfer.senderId());
            data.put("senderPhone", transfer.senderPhone());
            data.put("senderFullName", transfer.senderFullName());
            data.put("receiverId", transfer.receiverId());
            data.put("receiverPhone", transfer.receiverPhone());
            data.put("receiverFullName", transfer.receiverFullName());
            data.put("senderBankCode", transfer.senderBankCode());
            data.put("receiverBankCode", transfer.receiverBankCode());
            data.put("amountMinor", amountMinor);
            data.put("amount", transfer.amount() != null ? transfer.amount().toPlainString() : "0.00");
            data.put("currency", transfer.currency() != null ? transfer.currency() : "LKR");
            data.put("status", transfer.status() != null ? transfer.status().name() : TransactionStatus.PENDING.name());
            data.put("failureReason", transfer.failureReason());
            data.put("bankTxRef", transfer.bankTxRef());
            data.put("providerReference", transfer.providerReference());
            data.put("note", transfer.note());
            data.put("simulated", transfer.simulated());
            data.put("initiatedVia", transfer.initiatedVia() != null ? transfer.initiatedVia().name() : TransferInitiatedVia.APP.name());
            data.put("qrCodeId", transfer.qrCodeId());
            data.put("version", 1L);
            data.put("participantIds", List.of(transfer.senderId(), transfer.receiverId()));
            data.put("createdAt", FieldValue.serverTimestamp());
            data.put("updatedAt", FieldValue.serverTimestamp());

            try {
                WriteBatch batch = firestore.batch();
                batch.set(txRef, data);

                // Write initial event
                Long eventId = nextEventId();
                DocumentReference eventRef = txRef.collection(EVENTS_SUBCOLLECTION).document(String.valueOf(eventId));
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("id", eventId);
                eventData.put("transferId", transferId);
                eventData.put("fromStatus", null);
                eventData.put("toStatus", transfer.status() != null ? transfer.status().name() : TransactionStatus.PENDING.name());
                eventData.put("reason", "INITIATED");
                eventData.put("detailJson", null);
                eventData.put("occurredAt", FieldValue.serverTimestamp());
                batch.set(eventRef, eventData);

                batch.commit().get();

                return new TransactionRecord(
                        transferId, transfer.reference(), transfer.idempotencyKey(),
                        transfer.senderId(), transfer.senderPhone(), transfer.senderFullName(),
                        transfer.receiverId(), transfer.receiverPhone(), transfer.receiverFullName(),
                        transfer.senderBankCode(), transfer.receiverBankCode(),
                        transfer.amount(), transfer.currency(),
                        transfer.status() != null ? transfer.status() : TransactionStatus.PENDING,
                        transfer.failureReason(), transfer.bankTxRef(), transfer.providerReference(),
                        transfer.note(), transfer.simulated(), transfer.initiatedVia(),
                        transfer.qrCodeId(), 1L, Instant.now(), Instant.now()
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        } else {
            // Update existing via transaction for concurrency safety
            return transitionTransferStatus(
                    transfer.id(),
                    transfer.status(),
                    transfer.status() != null ? transfer.status().name() : "UPDATE",
                    null,
                    transfer.bankTxRef(),
                    transfer.providerReference(),
                    transfer.failureReason(),
                    transfer.version()
            );
        }
    }

    // ── Atomic Multi-Document Transactions ────────────────────

    @Override
    public TransactionRecord createTransferWithIdempotency(
            TransactionRecord transfer,
            String idempotencyKey,
            String payloadHash) {

        DocumentReference idemRef = firestore.collection(IDEMPOTENCY_COLLECTION).document(idempotencyKey);

        return txHelper.runTransaction(tx -> {
            // 1. Strictly all reads first
            DocumentSnapshot idemSnap = tx.get(idemRef).get();

            if (idemSnap.exists()) {
                String existingHash = idemSnap.getString("payloadHash");
                if (existingHash != null && !existingHash.equals(payloadHash)) {
                    throw new ConflictException("IDEMPOTENCY_CONFLICT",
                            "Idempotency key '" + idempotencyKey + "' was already used with a different request payload");
                }
                Long existingTxId = idemSnap.getLong("txId");
                if (existingTxId == null) {
                    existingTxId = idemSnap.getLong("transferId");
                }
                if (existingTxId != null) {
                    DocumentReference existingTxRef = firestore.collection(TRANSACTIONS_COLLECTION)
                            .document(String.valueOf(existingTxId));
                    DocumentSnapshot existingTxSnap = tx.get(existingTxRef).get();
                    if (existingTxSnap.exists()) {
                        log.info("Idempotency match inside transaction: returning existing transfer id={}", existingTxId);
                        return mapTransaction(existingTxSnap);
                    }
                }
            }

            // 2. Perform writes in the same transaction
            Long transferId = transfer.id() != null ? transfer.id() : nextTransferId();
            DocumentReference txRef = firestore.collection(TRANSACTIONS_COLLECTION).document(String.valueOf(transferId));

            long amountMinor = transfer.amount() != null
                    ? transfer.amount().movePointRight(2).longValue()
                    : 0L;

            Map<String, Object> txData = new HashMap<>();
            txData.put("id", transferId);
            txData.put("reference", transfer.reference());
            txData.put("idempotencyKey", idempotencyKey);
            txData.put("senderId", transfer.senderId());
            txData.put("senderPhone", transfer.senderPhone());
            txData.put("senderFullName", transfer.senderFullName());
            txData.put("receiverId", transfer.receiverId());
            txData.put("receiverPhone", transfer.receiverPhone());
            txData.put("receiverFullName", transfer.receiverFullName());
            txData.put("senderBankCode", transfer.senderBankCode());
            txData.put("receiverBankCode", transfer.receiverBankCode());
            txData.put("amountMinor", amountMinor);
            txData.put("amount", transfer.amount() != null ? transfer.amount().toPlainString() : "0.00");
            txData.put("currency", transfer.currency() != null ? transfer.currency() : "LKR");
            txData.put("status", TransactionStatus.PENDING.name());
            txData.put("failureReason", null);
            txData.put("bankTxRef", null);
            txData.put("providerReference", null);
            txData.put("note", transfer.note());
            txData.put("simulated", transfer.simulated());
            txData.put("initiatedVia", transfer.initiatedVia() != null ? transfer.initiatedVia().name() : TransferInitiatedVia.APP.name());
            txData.put("qrCodeId", transfer.qrCodeId());
            txData.put("version", 1L);
            txData.put("participantIds", List.of(transfer.senderId(), transfer.receiverId()));
            txData.put("createdAt", FieldValue.serverTimestamp());
            txData.put("updatedAt", FieldValue.serverTimestamp());

            tx.set(txRef, txData);

            // Append initial event in subcollection
            Long eventId = nextEventId();
            DocumentReference eventRef = txRef.collection(EVENTS_SUBCOLLECTION).document(String.valueOf(eventId));
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("id", eventId);
            eventData.put("transferId", transferId);
            eventData.put("fromStatus", null);
            eventData.put("toStatus", TransactionStatus.PENDING.name());
            eventData.put("reason", "INITIATED");
            eventData.put("detailJson", null);
            eventData.put("occurredAt", FieldValue.serverTimestamp());
            tx.set(eventRef, eventData);

            // Idempotency document
            Map<String, Object> idemData = new HashMap<>();
            idemData.put("idempotencyKey", idempotencyKey);
            idemData.put("payloadHash", payloadHash);
            idemData.put("txId", transferId);
            idemData.put("transferId", transferId);
            idemData.put("createdAt", FieldValue.serverTimestamp());
            tx.set(idemRef, idemData);

            return new TransactionRecord(
                    transferId, transfer.reference(), idempotencyKey,
                    transfer.senderId(), transfer.senderPhone(), transfer.senderFullName(),
                    transfer.receiverId(), transfer.receiverPhone(), transfer.receiverFullName(),
                    transfer.senderBankCode(), transfer.receiverBankCode(),
                    transfer.amount(), transfer.currency(),
                    TransactionStatus.PENDING, null, null, null,
                    transfer.note(), transfer.simulated(), transfer.initiatedVia(),
                    transfer.qrCodeId(), 1L, Instant.now(), Instant.now()
            );
        });
    }

    @Override
    public TransactionRecord transitionTransferStatus(
            Long transferId,
            TransactionStatus targetStatus,
            String reason,
            String detailJson,
            String bankTxRef,
            String providerRef,
            String failureReason,
            Long expectedVersion) {

        DocumentReference txRef = firestore.collection(TRANSACTIONS_COLLECTION).document(String.valueOf(transferId));

        return txHelper.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(txRef).get();
            if (!snap.exists()) {
                throw new ResourceNotFoundException("TRANSFER", transferId);
            }

            String currentStatusStr = snap.getString("status");
            TransactionStatus currentStatus = currentStatusStr != null
                    ? TransactionStatus.valueOf(currentStatusStr)
                    : TransactionStatus.PENDING;

            // Validate transition with state machine if status is changing
            if (currentStatus != targetStatus) {
                TransactionStateMachine.validateTransition(currentStatus, targetStatus);
            }

            Long currentVersion = snap.getLong("version") != null ? snap.getLong("version") : 1L;
            if (expectedVersion != null && !expectedVersion.equals(currentVersion)) {
                throw new ConflictException("OPTIMISTIC_LOCK_FAILURE",
                        "Transaction version mismatch: expected " + expectedVersion + " but was " + currentVersion);
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("status", targetStatus.name());
            updates.put("version", currentVersion + 1);
            updates.put("updatedAt", FieldValue.serverTimestamp());
            if (bankTxRef != null) updates.put("bankTxRef", bankTxRef);
            if (providerRef != null) updates.put("providerReference", providerRef);
            if (failureReason != null) updates.put("failureReason", failureReason);

            tx.update(txRef, updates);

            // Append-only event in subcollection inside SAME transaction
            Long eventId = nextEventId();
            DocumentReference eventRef = txRef.collection(EVENTS_SUBCOLLECTION).document(String.valueOf(eventId));
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("id", eventId);
            eventData.put("transferId", transferId);
            eventData.put("fromStatus", currentStatus.name());
            eventData.put("toStatus", targetStatus.name());
            eventData.put("reason", reason);
            eventData.put("detailJson", detailJson);
            eventData.put("occurredAt", FieldValue.serverTimestamp());
            tx.set(eventRef, eventData);

            TransactionRecord existing = mapTransaction(snap);
            return new TransactionRecord(
                    existing.id(), existing.reference(), existing.idempotencyKey(),
                    existing.senderId(), existing.senderPhone(), existing.senderFullName(),
                    existing.receiverId(), existing.receiverPhone(), existing.receiverFullName(),
                    existing.senderBankCode(), existing.receiverBankCode(),
                    existing.amount(), existing.currency(), targetStatus,
                    failureReason != null ? failureReason : existing.failureReason(),
                    bankTxRef != null ? bankTxRef : existing.bankTxRef(),
                    providerRef != null ? providerRef : existing.providerReference(),
                    existing.note(), existing.simulated(), existing.initiatedVia(),
                    existing.qrCodeId(), currentVersion + 1,
                    existing.createdAt(), Instant.now()
            );
        });
    }

    // ── Queries & History ──────────────────────────────────────

    @Override
    public Page<TransactionRecord> findTransfersBySenderId(Long senderId, Pageable pageable) {
        return searchTransfers(senderId, TransferDirection.SENT, null, null, null, null, null, null, pageable);
    }

    @Override
    public Page<TransactionRecord> findTransfersByReceiverId(Long receiverId, Pageable pageable) {
        return searchTransfers(receiverId, TransferDirection.RECEIVED, null, null, null, null, null, null, pageable);
    }

    @Override
    public Page<TransactionRecord> findTransfersByUserId(Long userId, Pageable pageable) {
        return searchTransfers(userId, TransferDirection.ALL, null, null, null, null, null, null, pageable);
    }

    @Override
    public Page<TransactionRecord> findTransfersByStatus(TransactionStatus status, Pageable pageable) {
        try {
            Query q = firestore.collection(TRANSACTIONS_COLLECTION)
                    .whereEqualTo("status", status.name())
                    .orderBy("createdAt", Query.Direction.DESCENDING);

            return executePagedQuery(q, pageable);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<TransactionRecord> findTransfersByStatusIn(Collection<TransactionStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) return Collections.emptyList();
        List<String> names = statuses.stream().map(Enum::name).toList();
        try {
            QuerySnapshot snap = firestore.collection(TRANSACTIONS_COLLECTION)
                    .whereIn("status", names)
                    .get().get();
            return snap.getDocuments().stream().map(this::mapTransaction).toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long countTransfersBySenderIdAndCreatedAtAfter(Long senderId, Instant since) {
        try {
            Timestamp ts = Timestamp.ofTimeSecondsAndNanos(since.getEpochSecond(), since.getNano());
            AggregateQuerySnapshot snap = firestore.collection(TRANSACTIONS_COLLECTION)
                    .whereEqualTo("senderId", senderId)
                    .whereGreaterThanOrEqualTo("createdAt", ts)
                    .count()
                    .get().get();
            return snap.getCount();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Search transfers with composite filters and cursor pagination.
     *
     * Firestore Limitations Handled:
     * - Firestore permits inequality filters (<, <=, >, >=) on only ONE field per query.
     * - A query cannot combine Date range inequalities on 'createdAt' with Amount range inequalities on 'amountMinor'.
     * - When both Date range and Amount range are specified, Firestore executes the Date range query with
     *   composite index (participantIds + createdAt DESC), and the Amount range is evaluated in-memory.
     * - Bank code filter across sender/receiver banks is evaluated in-memory when direction is ALL.
     */
    @Override
    public Page<TransactionRecord> searchTransfers(
            Long userId,
            TransferDirection direction,
            TransactionStatus status,
            Instant fromTime,
            Instant toTime,
            String bankCode,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable) {

        Query q = firestore.collection(TRANSACTIONS_COLLECTION);

        // 1. Participant / Direction scoping
        if (userId != null) {
            if (direction == TransferDirection.SENT) {
                q = q.whereEqualTo("senderId", userId);
            } else if (direction == TransferDirection.RECEIVED) {
                q = q.whereEqualTo("receiverId", userId);
            } else {
                q = q.whereArrayContains("participantIds", userId);
            }
        }

        // 2. Status equality filter
        if (status != null) {
            q = q.whereEqualTo("status", status.name());
        }

        // 3. Date range inequality on createdAt
        boolean hasDateInequality = false;
        if (fromTime != null) {
            q = q.whereGreaterThanOrEqualTo("createdAt", Timestamp.ofTimeSecondsAndNanos(fromTime.getEpochSecond(), fromTime.getNano()));
            hasDateInequality = true;
        }
        if (toTime != null) {
            q = q.whereLessThanOrEqualTo("createdAt", Timestamp.ofTimeSecondsAndNanos(toTime.getEpochSecond(), toTime.getNano()));
            hasDateInequality = true;
        }

        // Always order by createdAt DESC
        q = q.orderBy("createdAt", Query.Direction.DESCENDING);

        // In-memory secondary filters if amount or bankCode specified
        boolean requiresMemoryFilter = minAmount != null || maxAmount != null || (bankCode != null && !bankCode.isBlank());

        try {
            if (!requiresMemoryFilter) {
                return executePagedQuery(q, pageable);
            }

            // Fetch larger window to apply in-memory secondary filtering
            QuerySnapshot snap = q.limit(500).get().get();
            Long minMinor = minAmount != null ? minAmount.movePointRight(2).longValue() : null;
            Long maxMinor = maxAmount != null ? maxAmount.movePointRight(2).longValue() : null;

            List<TransactionRecord> filtered = snap.getDocuments().stream()
                    .map(this::mapTransaction)
                    .filter(tx -> {
                        if (minMinor != null) {
                            long txMinor = tx.amount().movePointRight(2).longValue();
                            if (txMinor < minMinor) return false;
                        }
                        if (maxMinor != null) {
                            long txMinor = tx.amount().movePointRight(2).longValue();
                            if (txMinor > maxMinor) return false;
                        }
                        if (bankCode != null && !bankCode.isBlank()) {
                            boolean bankMatch = bankCode.equalsIgnoreCase(tx.senderBankCode())
                                    || bankCode.equalsIgnoreCase(tx.receiverBankCode());
                            if (!bankMatch) return false;
                        }
                        return true;
                    })
                    .toList();

            int offset = (int) pageable.getOffset();
            int limit = pageable.getPageSize();
            if (offset >= filtered.size()) {
                return new PageImpl<>(Collections.emptyList(), pageable, filtered.size());
            }
            int end = Math.min(offset + limit, filtered.size());
            return new PageImpl<>(filtered.subList(offset, end), pageable, filtered.size());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private Page<TransactionRecord> executePagedQuery(Query query, Pageable pageable) {
        try {
            int offset = (int) pageable.getOffset();
            int limit = pageable.getPageSize();

            AggregateQuerySnapshot countSnap = query.count().get().get();
            long total = countSnap.getCount();

            Query pagedQuery = query.offset(offset).limit(limit);
            QuerySnapshot snap = pagedQuery.get().get();
            List<TransactionRecord> records = snap.getDocuments().stream().map(this::mapTransaction).toList();
            return new PageImpl<>(records, pageable, total);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    // ── Transaction Events ────────────────────────────────

    @Override
    public TransactionEventRecord saveTransactionEvent(TransactionEventRecord event) {
        Long eventId = event.id() != null ? event.id() : nextEventId();
        Long transferId = event.transferId();
        if (transferId == null) {
            throw new IllegalArgumentException("transferId cannot be null for TransactionEvent");
        }

        DocumentReference ref = firestore.collection(TRANSACTIONS_COLLECTION)
                .document(String.valueOf(transferId))
                .collection(EVENTS_SUBCOLLECTION)
                .document(String.valueOf(eventId));

        Map<String, Object> data = new HashMap<>();
        data.put("id", eventId);
        data.put("transferId", transferId);
        data.put("fromStatus", event.fromStatus() != null ? event.fromStatus().name() : null);
        data.put("toStatus", event.toStatus() != null ? event.toStatus().name() : null);
        data.put("reason", event.reason());
        data.put("detailJson", event.detailJson());
        data.put("occurredAt", FieldValue.serverTimestamp());

        try {
            ref.set(data).get();
            return new TransactionEventRecord(
                    eventId, transferId, event.fromStatus(), event.toStatus(),
                    event.reason(), event.detailJson(), Instant.now()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<TransactionEventRecord> findEventsByTransferId(Long transferId) {
        if (transferId == null) return Collections.emptyList();
        try {
            QuerySnapshot snap = firestore.collection(TRANSACTIONS_COLLECTION)
                    .document(String.valueOf(transferId))
                    .collection(EVENTS_SUBCOLLECTION)
                    .orderBy("occurredAt", Query.Direction.ASCENDING)
                    .get().get();
            return snap.getDocuments().stream().map(this::mapEvent).toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<TransactionEventRecord> findEventsByToStatus(TransactionStatus toStatus) {
        if (toStatus == null) return Collections.emptyList();
        try {
            QuerySnapshot snap = firestore.collectionGroup(EVENTS_SUBCOLLECTION)
                    .whereEqualTo("toStatus", toStatus.name())
                    .get().get();
            return snap.getDocuments().stream().map(this::mapEvent).toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    // ── Daily Limits ──────────────────────────────────────

    @Override
    public Optional<DailyLimitRecord> findDailyLimit(Long userId, LocalDate date) {
        if (userId == null || date == null) return Optional.empty();
        String docId = userId + "_" + date;
        try {
            DocumentSnapshot snap = firestore.collection(DAILY_LIMITS_COLLECTION).document(docId).get().get();
            if (!snap.exists()) return Optional.empty();
            return Optional.of(mapDailyLimit(snap));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DailyLimitRecord saveDailyLimit(DailyLimitRecord dailyLimit) {
        String docId = dailyLimit.userId() + "_" + dailyLimit.limitDate();
        DocumentReference ref = firestore.collection(DAILY_LIMITS_COLLECTION).document(docId);

        long totalSentMinor = dailyLimit.totalSent() != null
                ? dailyLimit.totalSent().movePointRight(2).longValue()
                : 0L;
        long dailyCapMinor = dailyLimit.dailyCap() != null
                ? dailyLimit.dailyCap().movePointRight(2).longValue()
                : 10000000L;

        Map<String, Object> data = new HashMap<>();
        data.put("id", dailyLimit.id() != null ? dailyLimit.id() : dailyLimit.userId());
        data.put("userId", dailyLimit.userId());
        data.put("limitDate", dailyLimit.limitDate().toString());
        data.put("totalSentMinor", totalSentMinor);
        data.put("totalSent", dailyLimit.totalSent() != null ? dailyLimit.totalSent().toPlainString() : "0.00");
        data.put("dailyCapMinor", dailyCapMinor);
        data.put("dailyCap", dailyLimit.dailyCap() != null ? dailyLimit.dailyCap().toPlainString() : "100000.00");
        data.put("updatedAt", FieldValue.serverTimestamp());

        try {
            ref.set(data, SetOptions.merge()).get();
            return dailyLimit;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    // ── Idempotency Records ───────────────────────────────

    @Override
    public Optional<IdempotencyRecordModel> findIdempotencyRecord(String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        try {
            DocumentSnapshot snap = firestore.collection(IDEMPOTENCY_COLLECTION).document(key).get().get();
            if (!snap.exists()) return Optional.empty();
            return Optional.of(mapIdempotency(snap));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean existsIdempotencyRecord(String key) {
        if (key == null || key.isBlank()) return false;
        try {
            DocumentSnapshot snap = firestore.collection(IDEMPOTENCY_COLLECTION).document(key).get().get();
            return snap.exists();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public IdempotencyRecordModel saveIdempotencyRecord(IdempotencyRecordModel record) {
        DocumentReference ref = firestore.collection(IDEMPOTENCY_COLLECTION).document(record.idempotencyKey());

        Map<String, Object> data = new HashMap<>();
        data.put("idempotencyKey", record.idempotencyKey());
        data.put("payloadHash", record.payloadHash());
        data.put("txId", record.transferId());
        data.put("transferId", record.transferId());
        if (record.responseJson() != null) {
            data.put("responseJson", record.responseJson());
        }
        if (record.httpStatus() != null) {
            data.put("httpStatus", record.httpStatus());
        }
        data.put("updatedAt", FieldValue.serverTimestamp());

        try {
            ref.set(data, SetOptions.merge()).get();
            return record;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    // ── Mapping Helpers ───────────────────────────────────

    private TransactionRecord mapTransaction(DocumentSnapshot doc) {
        Long id = doc.getLong("id");
        Long amountMinor = doc.getLong("amountMinor");
        BigDecimal amount = amountMinor != null
                ? BigDecimal.valueOf(amountMinor, 2)
                : (doc.getString("amount") != null ? new BigDecimal(doc.getString("amount")) : BigDecimal.ZERO);

        String statusStr = doc.getString("status");
        TransactionStatus status = statusStr != null ? TransactionStatus.valueOf(statusStr) : TransactionStatus.PENDING;

        String viaStr = doc.getString("initiatedVia");
        TransferInitiatedVia via = viaStr != null ? TransferInitiatedVia.valueOf(viaStr) : TransferInitiatedVia.APP;
        Boolean sim = doc.getBoolean("simulated");

        return new TransactionRecord(
                id,
                doc.getString("reference"),
                doc.getString("idempotencyKey"),
                doc.getLong("senderId"),
                doc.getString("senderPhone"),
                doc.getString("senderFullName"),
                doc.getLong("receiverId"),
                doc.getString("receiverPhone"),
                doc.getString("receiverFullName"),
                doc.getString("senderBankCode"),
                doc.getString("receiverBankCode"),
                amount,
                doc.getString("currency") != null ? doc.getString("currency") : "LKR",
                status,
                doc.getString("failureReason"),
                doc.getString("bankTxRef"),
                doc.getString("providerReference"),
                doc.getString("note"),
                sim != null && sim,
                via,
                doc.getLong("qrCodeId"),
                doc.getLong("version") != null ? doc.getLong("version") : 1L,
                toInstant(doc.getTimestamp("createdAt")),
                toInstant(doc.getTimestamp("updatedAt"))
        );
    }

    private TransactionEventRecord mapEvent(DocumentSnapshot doc) {
        String fromStr = doc.getString("fromStatus");
        String toStr = doc.getString("toStatus");
        return new TransactionEventRecord(
                doc.getLong("id"),
                doc.getLong("transferId"),
                fromStr != null ? TransactionStatus.valueOf(fromStr) : null,
                toStr != null ? TransactionStatus.valueOf(toStr) : null,
                doc.getString("reason"),
                doc.getString("detailJson"),
                toInstant(doc.getTimestamp("occurredAt"))
        );
    }

    private DailyLimitRecord mapDailyLimit(DocumentSnapshot doc) {
        Long sentMinor = doc.getLong("totalSentMinor");
        BigDecimal totalSent = sentMinor != null
                ? BigDecimal.valueOf(sentMinor, 2)
                : (doc.getString("totalSent") != null ? new BigDecimal(doc.getString("totalSent")) : BigDecimal.ZERO);

        Long capMinor = doc.getLong("dailyCapMinor");
        BigDecimal dailyCap = capMinor != null
                ? BigDecimal.valueOf(capMinor, 2)
                : (doc.getString("dailyCap") != null ? new BigDecimal(doc.getString("dailyCap")) : new BigDecimal("100000.00"));

        String dateStr = doc.getString("limitDate");
        LocalDate date = dateStr != null ? LocalDate.parse(dateStr) : LocalDate.now();

        return new DailyLimitRecord(
                doc.getLong("id"),
                doc.getLong("userId"),
                date,
                totalSent,
                dailyCap
        );
    }

    private IdempotencyRecordModel mapIdempotency(DocumentSnapshot doc) {
        Long txId = doc.getLong("txId");
        if (txId == null) txId = doc.getLong("transferId");
        Long statusLong = doc.getLong("httpStatus");
        Integer httpStatus = statusLong != null ? statusLong.intValue() : 200;

        return new IdempotencyRecordModel(
                doc.getLong("id"),
                doc.getString("idempotencyKey") != null ? doc.getString("idempotencyKey") : doc.getId(),
                doc.getString("payloadHash"),
                txId,
                doc.getString("responseJson"),
                httpStatus,
                toInstant(doc.getTimestamp("createdAt"))
        );
    }

    private Instant toInstant(Timestamp ts) {
        return ts != null ? Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos()) : Instant.now();
    }
}
