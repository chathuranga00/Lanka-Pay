package com.lankapay.notification.store.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.lankapay.notification.domain.*;
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
 * Firestore implementation of {@link NotificationStore}.
 *
 * Requirements:
 * - notifications collection: document ID = notification ID.
 * - notificationPreferences collection: document ID = user ID.
 * - Delivery failure handling must never affect payment flows.
 */
@Component
public class FirestoreNotificationStore implements NotificationStore {

    private static final Logger log = LoggerFactory.getLogger(FirestoreNotificationStore.class);

    public static final String NOTIFICATIONS_COLLECTION = "notifications";
    public static final String PREFERENCES_COLLECTION = "notificationPreferences";

    private final Firestore firestore;
    private final AtomicLong fallbackIdGen = new AtomicLong(System.currentTimeMillis() % 100000000L);

    public FirestoreNotificationStore(Firestore firestore) {
        this.firestore = firestore;
    }

    private Long nextId() {
        return fallbackIdGen.incrementAndGet();
    }

    @Override
    public NotificationRecord saveNotification(NotificationRecord record) {
        Long id = record.id() != null ? record.id() : nextId();
        DocumentReference docRef = firestore.collection(NOTIFICATIONS_COLLECTION).document(String.valueOf(id));

        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("userId", record.userId());
        data.put("channel", record.channel() != null ? record.channel().name() : null);
        data.put("type", record.type() != null ? record.type().name() : null);
        data.put("title", record.title());
        data.put("message", record.message());
        data.put("status", record.status() != null ? record.status().name() : NotificationStatus.PENDING.name());
        data.put("failureReason", record.failureReason());
        data.put("read", record.read());
        data.put("readAt", record.readAt() != null ? toTimestamp(record.readAt()) : null);
        data.put("createdAt", record.createdAt() != null ? toTimestamp(record.createdAt()) : FieldValue.serverTimestamp());
        data.put("simulated", record.simulated());

        try {
            docRef.set(data).get();
            return new NotificationRecord(
                    id,
                    record.userId(),
                    record.channel(),
                    record.type(),
                    record.title(),
                    record.message(),
                    record.status(),
                    record.failureReason(),
                    record.read(),
                    record.readAt(),
                    record.createdAt() != null ? record.createdAt() : Instant.now(),
                    record.simulated()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while saving notification", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Error saving notification: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<NotificationRecord> findNotificationByIdAndUserId(Long id, Long userId) {
        if (id == null || userId == null) return Optional.empty();
        try {
            DocumentSnapshot snap = firestore.collection(NOTIFICATIONS_COLLECTION)
                    .document(String.valueOf(id))
                    .get()
                    .get();

            if (!snap.exists()) return Optional.empty();
            NotificationRecord record = mapNotification(snap);
            if (!userId.equals(record.userId())) return Optional.empty();
            return Optional.of(record);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Page<NotificationRecord> findNotificationsByUserId(Long userId, Pageable pageable) {
        Query query = firestore.collection(NOTIFICATIONS_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING);
        return executePagedQuery(query, pageable);
    }

    @Override
    public Page<NotificationRecord> findNotificationsByUserIdAndRead(Long userId, boolean read, Pageable pageable) {
        Query query = firestore.collection(NOTIFICATIONS_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereEqualTo("read", read)
                .orderBy("createdAt", Query.Direction.DESCENDING);
        return executePagedQuery(query, pageable);
    }

    @Override
    public Page<NotificationRecord> findNotificationsByUserIdAndType(Long userId, NotificationType type, Pageable pageable) {
        Query query = firestore.collection(NOTIFICATIONS_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereEqualTo("type", type.name())
                .orderBy("createdAt", Query.Direction.DESCENDING);
        return executePagedQuery(query, pageable);
    }

    @Override
    public long countUnreadNotifications(Long userId) {
        if (userId == null) return 0;
        try {
            AggregateQuerySnapshot countSnap = firestore.collection(NOTIFICATIONS_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("read", false)
                    .count()
                    .get()
                    .get();
            return countSnap.getCount();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int markAllNotificationsAsReadForUser(Long userId, Instant now) {
        if (userId == null) return 0;
        try {
            QuerySnapshot snap = firestore.collection(NOTIFICATIONS_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("read", false)
                    .get()
                    .get();

            WriteBatch batch = firestore.batch();
            for (DocumentSnapshot doc : snap.getDocuments()) {
                batch.update(doc.getReference(), "read", true, "readAt", toTimestamp(now));
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

    @Override
    public int deleteNotificationsOlderThan(Instant cutoff) {
        try {
            QuerySnapshot snap = firestore.collection(NOTIFICATIONS_COLLECTION)
                    .whereLessThan("createdAt", toTimestamp(cutoff))
                    .get()
                    .get();

            WriteBatch batch = firestore.batch();
            for (DocumentSnapshot doc : snap.getDocuments()) {
                batch.delete(doc.getReference());
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

    @Override
    public Optional<NotificationPreferenceRecord> findPreferenceByUserId(Long userId) {
        if (userId == null) return Optional.empty();
        try {
            DocumentSnapshot snap = firestore.collection(PREFERENCES_COLLECTION)
                    .document(String.valueOf(userId))
                    .get()
                    .get();

            if (!snap.exists()) return Optional.empty();
            return Optional.of(mapPreference(snap));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public NotificationPreferenceRecord savePreference(NotificationPreferenceRecord preference) {
        Long userId = preference.userId();
        DocumentReference docRef = firestore.collection(PREFERENCES_COLLECTION).document(String.valueOf(userId));

        Map<String, Object> data = new HashMap<>();
        data.put("id", preference.id() != null ? preference.id() : userId);
        data.put("userId", userId);
        data.put("inAppEnabled", preference.inAppEnabled());
        data.put("pushEnabled", preference.pushEnabled());
        data.put("emailEnabled", preference.emailEnabled());
        data.put("smsEnabled", preference.smsEnabled());
        data.put("paymentAlerts", preference.paymentAlerts());
        data.put("securityAlerts", preference.securityAlerts());
        data.put("updatedAt", FieldValue.serverTimestamp());

        try {
            docRef.set(data).get();
            return preference;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private Page<NotificationRecord> executePagedQuery(Query query, Pageable pageable) {
        try {
            int offset = (int) pageable.getOffset();
            int limit = pageable.getPageSize();

            AggregateQuerySnapshot countSnap = query.count().get().get();
            long total = countSnap.getCount();

            Query paged = query.offset(offset).limit(limit);
            QuerySnapshot snap = paged.get().get();
            List<NotificationRecord> records = snap.getDocuments().stream().map(this::mapNotification).toList();

            return new PageImpl<>(records, pageable, total);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private NotificationRecord mapNotification(DocumentSnapshot doc) {
        Long id = doc.getLong("id");
        Long userId = doc.getLong("userId");

        String channelStr = doc.getString("channel");
        NotificationChannelType channel = channelStr != null ? NotificationChannelType.valueOf(channelStr) : null;

        String typeStr = doc.getString("type");
        NotificationType type = typeStr != null ? NotificationType.valueOf(typeStr) : null;

        String title = doc.getString("title");
        String message = doc.getString("message");

        String statusStr = doc.getString("status");
        NotificationStatus status = statusStr != null ? NotificationStatus.valueOf(statusStr) : NotificationStatus.PENDING;

        String failureReason = doc.getString("failureReason");
        Boolean read = doc.getBoolean("read");
        Instant readAt = toInstant(doc.getTimestamp("readAt"));
        Instant createdAt = toInstant(doc.getTimestamp("createdAt"));
        Boolean simulated = doc.getBoolean("simulated");

        return new NotificationRecord(
                id, userId, channel, type, title, message, status, failureReason,
                Boolean.TRUE.equals(read), readAt, createdAt, Boolean.TRUE.equals(simulated)
        );
    }

    private NotificationPreferenceRecord mapPreference(DocumentSnapshot doc) {
        Long id = doc.getLong("id");
        Long userId = doc.getLong("userId");
        Boolean inApp = doc.getBoolean("inAppEnabled");
        Boolean push = doc.getBoolean("pushEnabled");
        Boolean email = doc.getBoolean("emailEnabled");
        Boolean sms = doc.getBoolean("smsEnabled");
        Boolean payments = doc.getBoolean("paymentAlerts");
        Boolean security = doc.getBoolean("securityAlerts");
        Instant updatedAt = toInstant(doc.getTimestamp("updatedAt"));

        return new NotificationPreferenceRecord(
                id, userId,
                Boolean.TRUE.equals(inApp),
                Boolean.TRUE.equals(push),
                Boolean.TRUE.equals(email),
                Boolean.TRUE.equals(sms),
                Boolean.TRUE.equals(payments),
                Boolean.TRUE.equals(security),
                updatedAt
        );
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano()) : null;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()) : null;
    }
}
