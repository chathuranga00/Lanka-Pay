package com.lankapay.audit.store.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.lankapay.audit.domain.AuditRecord;
import com.lankapay.audit.domain.AuditStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Firestore implementation of {@link AuditStore}.
 *
 * Rules:
 * - Append-only audit logs in collection "auditLogs/{id}".
 * - Never modified or deleted.
 */
@Component
public class FirestoreAuditStore implements AuditStore {

    private static final Logger log = LoggerFactory.getLogger(FirestoreAuditStore.class);

    public static final String AUDIT_LOGS_COLLECTION = "auditLogs";

    private final Firestore firestore;
    private final AtomicLong fallbackIdGen = new AtomicLong(System.currentTimeMillis() % 100000000L);

    public FirestoreAuditStore(Firestore firestore) {
        this.firestore = firestore;
    }

    private Long nextId() {
        return fallbackIdGen.incrementAndGet();
    }

    @Override
    public AuditRecord save(AuditRecord record) {
        Long id = record.id() != null ? record.id() : nextId();
        DocumentReference ref = firestore.collection(AUDIT_LOGS_COLLECTION).document(String.valueOf(id));

        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("actorId", record.actorId());
        data.put("eventType", record.eventType());
        data.put("entityType", record.entityType());
        data.put("entityId", record.entityId());
        data.put("detailJson", record.detailJson());
        data.put("ipAddress", record.ipAddress());
        data.put("occurredAt", record.occurredAt() != null ? toTimestamp(record.occurredAt()) : FieldValue.serverTimestamp());

        try {
            ref.set(data).get();
            return new AuditRecord(
                    id,
                    record.actorId(),
                    record.eventType(),
                    record.entityType(),
                    record.entityId(),
                    record.detailJson(),
                    record.ipAddress(),
                    record.occurredAt() != null ? record.occurredAt() : Instant.now()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<AuditRecord> findById(Long id) {
        if (id == null) return Optional.empty();
        try {
            DocumentSnapshot snap = firestore.collection(AUDIT_LOGS_COLLECTION).document(String.valueOf(id)).get().get();
            if (!snap.exists()) return Optional.empty();
            return Optional.of(mapAudit(snap));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Page<AuditRecord> findAll(Pageable pageable) {
        try {
            QuerySnapshot snap = firestore.collection(AUDIT_LOGS_COLLECTION)
                    .orderBy("occurredAt", Query.Direction.DESCENDING)
                    .limit(pageable.getPageSize())
                    .get().get();

            List<AuditRecord> records = snap.getDocuments().stream().map(this::mapAudit).toList();
            return new PageImpl<>(records, pageable, records.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Page<AuditRecord> findByActorId(Long actorId, Pageable pageable) {
        if (actorId == null) return Page.empty();
        try {
            QuerySnapshot snap = firestore.collection(AUDIT_LOGS_COLLECTION)
                    .whereEqualTo("actorId", actorId)
                    .orderBy("occurredAt", Query.Direction.DESCENDING)
                    .limit(pageable.getPageSize())
                    .get().get();

            List<AuditRecord> records = snap.getDocuments().stream().map(this::mapAudit).toList();
            return new PageImpl<>(records, pageable, records.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private AuditRecord mapAudit(DocumentSnapshot snap) {
        Long id = snap.getLong("id");
        return new AuditRecord(
                id != null ? id : 1L,
                snap.getLong("actorId"),
                snap.getString("eventType"),
                snap.getString("entityType"),
                snap.getLong("entityId"),
                snap.getString("detailJson"),
                snap.getString("ipAddress"),
                toInstant(snap.getTimestamp("occurredAt"))
        );
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano()) : null;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()) : null;
    }
}
