package com.lankapay.admin.store.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.lankapay.admin.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Firestore implementation of {@link AdminStore}.
 *
 * Requirements:
 * - riskConfig collection: document ID = "default".
 * - fraudAlerts collection: document ID = alert ID.
 */
@Component
public class FirestoreAdminStore implements AdminStore {

    private static final Logger log = LoggerFactory.getLogger(FirestoreAdminStore.class);

    public static final String RISK_CONFIG_COLLECTION = "riskConfig";
    public static final String RISK_CONFIG_DOC_ID = "default";
    public static final String FRAUD_ALERTS_COLLECTION = "fraudAlerts";

    private final Firestore firestore;
    private final AtomicLong fallbackIdGen = new AtomicLong(System.currentTimeMillis() % 100000000L);

    public FirestoreAdminStore(Firestore firestore) {
        this.firestore = firestore;
    }

    private Long nextId() {
        return fallbackIdGen.incrementAndGet();
    }

    // ─────────────────────────────────────────────────────────────
    // Risk Rules
    // ─────────────────────────────────────────────────────────────

    @Override
    public Optional<RiskRulesRecord> findRiskRules() {
        try {
            DocumentSnapshot snap = firestore.collection(RISK_CONFIG_COLLECTION)
                    .document(RISK_CONFIG_DOC_ID)
                    .get()
                    .get();

            if (!snap.exists()) return Optional.empty();
            return Optional.of(mapRiskRules(snap));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public RiskRulesRecord saveRiskRules(RiskRulesRecord rules) {
        DocumentReference docRef = firestore.collection(RISK_CONFIG_COLLECTION).document(RISK_CONFIG_DOC_ID);

        Map<String, Object> data = new HashMap<>();
        data.put("id", 1L);
        if (rules.maxTransactionAmount() != null) {
            data.put("maxTransactionAmount", rules.maxTransactionAmount().doubleValue());
        }
        if (rules.maxDailyAmount() != null) {
            data.put("maxDailyAmount", rules.maxDailyAmount().doubleValue());
        }
        data.put("maxVelocityCountPerHour", rules.maxVelocityCountPerHour());
        data.put("newAccountAgeHours", rules.newAccountAgeHours());
        if (rules.newAccountMaxTransactionAmount() != null) {
            data.put("newAccountMaxTransactionAmount", rules.newAccountMaxTransactionAmount().doubleValue());
        }
        if (rules.newAccountMaxDailyAmount() != null) {
            data.put("newAccountMaxDailyAmount", rules.newAccountMaxDailyAmount().doubleValue());
        }
        data.put("velocityWindowMinutes", rules.velocityWindowMinutes());
        data.put("enabled", rules.enabled());
        data.put("createdAt", rules.createdAt() != null ? toTimestamp(rules.createdAt()) : FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());
        data.put("updatedBy", rules.updatedBy());

        try {
            docRef.set(data).get();
            return rules;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Fraud Alerts
    // ─────────────────────────────────────────────────────────────

    @Override
    public Optional<FraudAlertRecord> findFraudAlertById(Long id) {
        if (id == null) return Optional.empty();
        try {
            DocumentSnapshot snap = firestore.collection(FRAUD_ALERTS_COLLECTION)
                    .document(String.valueOf(id))
                    .get()
                    .get();

            if (!snap.exists()) return Optional.empty();
            return Optional.of(mapFraudAlert(snap));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public FraudAlertRecord saveFraudAlert(FraudAlertRecord alert) {
        Long id = alert.id() != null ? alert.id() : nextId();
        DocumentReference docRef = firestore.collection(FRAUD_ALERTS_COLLECTION).document(String.valueOf(id));

        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("userId", alert.userId());
        data.put("userPhone", alert.userPhone());
        data.put("userFullName", alert.userFullName());
        data.put("transferId", alert.transferId());
        data.put("transferReference", alert.transferReference());
        data.put("alertType", alert.alertType() != null ? alert.alertType().name() : null);
        data.put("severity", alert.severity() != null ? alert.severity().name() : null);
        data.put("status", alert.status() != null ? alert.status().name() : FraudAlertStatus.OPEN.name());
        data.put("description", alert.description());
        data.put("metadataJson", alert.metadataJson());
        data.put("createdAt", alert.createdAt() != null ? toTimestamp(alert.createdAt()) : FieldValue.serverTimestamp());
        data.put("resolvedAt", alert.resolvedAt() != null ? toTimestamp(alert.resolvedAt()) : null);
        data.put("resolvedBy", alert.resolvedBy());
        data.put("resolutionNotes", alert.resolutionNotes());

        try {
            docRef.set(data).get();
            return new FraudAlertRecord(
                    id,
                    alert.userId(),
                    alert.userPhone(),
                    alert.userFullName(),
                    alert.transferId(),
                    alert.transferReference(),
                    alert.alertType(),
                    alert.severity(),
                    alert.status(),
                    alert.description(),
                    alert.metadataJson(),
                    alert.createdAt() != null ? alert.createdAt() : Instant.now(),
                    alert.resolvedAt(),
                    alert.resolvedBy(),
                    alert.resolutionNotes()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Page<FraudAlertRecord> findFraudAlertsByStatus(FraudAlertStatus status, Pageable pageable) {
        Query query = firestore.collection(FRAUD_ALERTS_COLLECTION)
                .whereEqualTo("status", status.name())
                .orderBy("createdAt", Query.Direction.DESCENDING);
        return executePagedQuery(query, pageable);
    }

    @Override
    public Page<FraudAlertRecord> findFraudAlertsByUserId(Long userId, Pageable pageable) {
        Query query = firestore.collection(FRAUD_ALERTS_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING);
        return executePagedQuery(query, pageable);
    }

    @Override
    public Page<FraudAlertRecord> findFraudAlertsByStatusAndSeverity(FraudAlertStatus status, FraudAlertSeverity severity, Pageable pageable) {
        Query query = firestore.collection(FRAUD_ALERTS_COLLECTION)
                .whereEqualTo("status", status.name())
                .whereEqualTo("severity", severity.name())
                .orderBy("createdAt", Query.Direction.DESCENDING);
        return executePagedQuery(query, pageable);
    }

    @Override
    public Page<FraudAlertRecord> findAllFraudAlerts(Pageable pageable) {
        Query query = firestore.collection(FRAUD_ALERTS_COLLECTION)
                .orderBy("createdAt", Query.Direction.DESCENDING);
        return executePagedQuery(query, pageable);
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private Page<FraudAlertRecord> executePagedQuery(Query query, Pageable pageable) {
        try {
            int offset = (int) pageable.getOffset();
            int limit = pageable.getPageSize();

            AggregateQuerySnapshot countSnap = query.count().get().get();
            long total = countSnap.getCount();

            Query paged = query.offset(offset).limit(limit);
            QuerySnapshot snap = paged.get().get();
            List<FraudAlertRecord> records = snap.getDocuments().stream().map(this::mapFraudAlert).toList();

            return new PageImpl<>(records, pageable, total);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private RiskRulesRecord mapRiskRules(DocumentSnapshot doc) {
        Long id = doc.getLong("id");
        BigDecimal maxTx = doc.contains("maxTransactionAmount") && doc.get("maxTransactionAmount") != null ?
                BigDecimal.valueOf(doc.getDouble("maxTransactionAmount")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal maxDaily = doc.contains("maxDailyAmount") && doc.get("maxDailyAmount") != null ?
                BigDecimal.valueOf(doc.getDouble("maxDailyAmount")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        Long vel = doc.getLong("maxVelocityCountPerHour");
        Long age = doc.getLong("newAccountAgeHours");
        BigDecimal newMaxTx = doc.contains("newAccountMaxTransactionAmount") && doc.get("newAccountMaxTransactionAmount") != null ?
                BigDecimal.valueOf(doc.getDouble("newAccountMaxTransactionAmount")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal newMaxDaily = doc.contains("newAccountMaxDailyAmount") && doc.get("newAccountMaxDailyAmount") != null ?
                BigDecimal.valueOf(doc.getDouble("newAccountMaxDailyAmount")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        Long window = doc.getLong("velocityWindowMinutes");
        Boolean enabled = doc.getBoolean("enabled");
        Instant createdAt = toInstant(doc.getTimestamp("createdAt"));
        Instant updatedAt = toInstant(doc.getTimestamp("updatedAt"));
        Long updatedBy = doc.getLong("updatedBy");

        return new RiskRulesRecord(
                id != null ? id : 1L,
                maxTx,
                maxDaily,
                vel != null ? vel.intValue() : 5,
                age != null ? age.intValue() : 24,
                newMaxTx,
                newMaxDaily,
                window != null ? window.intValue() : 60,
                Boolean.TRUE.equals(enabled),
                createdAt,
                updatedAt,
                updatedBy
        );
    }

    private FraudAlertRecord mapFraudAlert(DocumentSnapshot doc) {
        Long id = doc.getLong("id");
        Long userId = doc.getLong("userId");
        String userPhone = doc.getString("userPhone");
        String userFullName = doc.getString("userFullName");
        Long transferId = doc.getLong("transferId");
        String transferReference = doc.getString("transferReference");

        String typeStr = doc.getString("alertType");
        FraudAlertType alertType = typeStr != null ? FraudAlertType.valueOf(typeStr) : null;

        String sevStr = doc.getString("severity");
        FraudAlertSeverity severity = sevStr != null ? FraudAlertSeverity.valueOf(sevStr) : null;

        String statusStr = doc.getString("status");
        FraudAlertStatus status = statusStr != null ? FraudAlertStatus.valueOf(statusStr) : FraudAlertStatus.OPEN;

        String description = doc.getString("description");
        String metadataJson = doc.getString("metadataJson");
        Instant createdAt = toInstant(doc.getTimestamp("createdAt"));
        Instant resolvedAt = toInstant(doc.getTimestamp("resolvedAt"));
        Long resolvedBy = doc.getLong("resolvedBy");
        String resolutionNotes = doc.getString("resolutionNotes");

        return new FraudAlertRecord(
                id, userId, userPhone, userFullName, transferId, transferReference,
                alertType, severity, status, description, metadataJson,
                createdAt, resolvedAt, resolvedBy, resolutionNotes
        );
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano()) : null;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()) : null;
    }
}
