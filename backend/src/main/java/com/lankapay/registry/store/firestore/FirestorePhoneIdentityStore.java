package com.lankapay.registry.store.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.firestore.FirestoreTransactionHelper;
import com.lankapay.registry.domain.AbuseCounterRecord;
import com.lankapay.registry.domain.PhoneIdentity;
import com.lankapay.registry.domain.PhoneIdentityStore;
import com.lankapay.registry.domain.RegistryAccountRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Firestore implementation of {@link PhoneIdentityStore}.
 *
 * Rules:
 * - Phone uniqueness: Document ID = normalized phone number in "phoneIdentities/{phone}".
 * - Enforces transactional verification and rejection if phone is already active and belongs to another user.
 * - Abuse counters stored under "abuseCounters/{actorId_date}".
 */
@Component
public class FirestorePhoneIdentityStore implements PhoneIdentityStore {

    private static final Logger log = LoggerFactory.getLogger(FirestorePhoneIdentityStore.class);

    public static final String PHONE_IDENTITIES_COLLECTION = "phoneIdentities";
    public static final String ABUSE_COUNTERS_COLLECTION = "abuseCounters";

    private final Firestore firestore;
    private final FirestoreTransactionHelper txHelper;
    private final AtomicLong fallbackIdGen = new AtomicLong(System.currentTimeMillis() % 100000000L);

    public FirestorePhoneIdentityStore(Firestore firestore, FirestoreTransactionHelper txHelper) {
        this.firestore = firestore;
        this.txHelper = txHelper;
    }

    private Long nextId() {
        return fallbackIdGen.incrementAndGet();
    }

    @Override
    public PhoneIdentity save(PhoneIdentity phoneIdentity) {
        String phoneDocId = phoneIdentity.phone();
        DocumentReference ref = firestore.collection(PHONE_IDENTITIES_COLLECTION).document(phoneDocId);

        return txHelper.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(ref).get();

            if (snap.exists()) {
                String existingUserId = snap.getString("userId");
                Boolean active = snap.getBoolean("active");
                boolean isActive = active == null || active;
                if (isActive && existingUserId != null && !existingUserId.equals(String.valueOf(phoneIdentity.userId()))) {
                    throw new ConflictException("PHONE_ALREADY_REGISTERED",
                            "This phone number is already registered to another account");
                }
            }

            Long id = phoneIdentity.id() != null ? phoneIdentity.id() : (snap.exists() && snap.getLong("id") != null ? snap.getLong("id") : nextId());

            Map<String, Object> data = new HashMap<>();
            data.put("id", id);
            data.put("userId", String.valueOf(phoneIdentity.userId()));
            data.put("phone", phoneIdentity.phone());
            data.put("discoverable", phoneIdentity.discoverable());
            data.put("userFullName", phoneIdentity.fullName());
            data.put("active", true);
            data.put("updatedAt", FieldValue.serverTimestamp());
            if (!snap.exists()) {
                data.put("claimedAt", FieldValue.serverTimestamp());
            }

            tx.set(ref, data, SetOptions.merge());

            DocumentReference paymentRegRef = firestore.collection("paymentRegistry").document(phoneDocId);
            Map<String, Object> regData = new HashMap<>();
            regData.put("phone", phoneIdentity.phone());
            regData.put("userId", phoneIdentity.userId());
            regData.put("displayName", phoneIdentity.fullName());
            regData.put("discoverable", phoneIdentity.discoverable());
            regData.put("updatedAt", FieldValue.serverTimestamp());
            tx.set(paymentRegRef, regData, SetOptions.merge());

            return new PhoneIdentity(
                    id,
                    phoneIdentity.userId(),
                    phoneIdentity.phone(),
                    phoneIdentity.discoverable(),
                    phoneIdentity.fullName(),
                    Instant.now()
            );
        });
    }

    @Override
    public Optional<PhoneIdentity> findByPhoneAndDiscoverableTrue(String phone) {
        if (phone == null || phone.isBlank()) return Optional.empty();
        try {
            DocumentSnapshot snap = firestore.collection(PHONE_IDENTITIES_COLLECTION).document(phone).get().get();
            if (!snap.exists()) return Optional.empty();
            Boolean discoverable = snap.getBoolean("discoverable");
            Boolean active = snap.getBoolean("active");
            if (Boolean.TRUE.equals(discoverable) && (active == null || active)) {
                return Optional.of(mapPhoneIdentity(snap));
            }
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<PhoneIdentity> findByUserId(Long userId) {
        if (userId == null) return Optional.empty();
        try {
            QuerySnapshot snap = firestore.collection(PHONE_IDENTITIES_COLLECTION)
                    .whereEqualTo("userId", String.valueOf(userId))
                    .limit(1)
                    .get().get();
            if (snap.isEmpty()) return Optional.empty();
            return Optional.of(mapPhoneIdentity(snap.getDocuments().get(0)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<PhoneIdentity> findByPhone(String phone) {
        if (phone == null || phone.isBlank()) return Optional.empty();
        try {
            DocumentSnapshot snap = firestore.collection(PHONE_IDENTITIES_COLLECTION).document(phone).get().get();
            if (!snap.exists()) return Optional.empty();
            return Optional.of(mapPhoneIdentity(snap));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<AbuseCounterRecord> findAbuseCounter(Long actorId, LocalDate date) {
        String docId = actorId + "_" + date.toString();
        try {
            DocumentSnapshot snap = firestore.collection(ABUSE_COUNTERS_COLLECTION).document(docId).get().get();
            if (!snap.exists()) return Optional.empty();
            return Optional.of(mapAbuseCounter(snap));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public AbuseCounterRecord saveAbuseCounter(AbuseCounterRecord counter) {
        String docId = counter.actorId() + "_" + counter.counterDate().toString();
        DocumentReference ref = firestore.collection(ABUSE_COUNTERS_COLLECTION).document(docId);

        Long id = counter.id() != null ? counter.id() : nextId();
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("actorId", counter.actorId());
        data.put("counterDate", counter.counterDate().toString());
        data.put("lookupCount", (long) counter.lookupCount());
        data.put("flagged", counter.flagged());

        try {
            ref.set(data, SetOptions.merge()).get();
            return new AbuseCounterRecord(id, counter.actorId(), counter.counterDate(), counter.lookupCount(), counter.flagged());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<RegistryAccountRecord> findAccountsInRegistry(String phone) {
        if (phone == null || phone.isBlank()) return List.of();
        try {
            QuerySnapshot snap = firestore.collection("paymentRegistry")
                    .document(phone)
                    .collection("accounts")
                    .get().get();
            return snap.getDocuments().stream()
                    .map(doc -> new RegistryAccountRecord(
                            doc.getLong("accountId"),
                            doc.getString("bankCode"),
                            doc.getString("bankName"),
                            doc.getString("maskedNumber"),
                            Boolean.TRUE.equals(doc.getBoolean("discoverable")),
                            Boolean.TRUE.equals(doc.getBoolean("receivingEnabled")),
                            Boolean.TRUE.equals(doc.getBoolean("isDefault")),
                            doc.getString("status") != null ? doc.getString("status") : "ACTIVE"
                    ))
                    .toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private PhoneIdentity mapPhoneIdentity(DocumentSnapshot snap) {
        Long id = snap.getLong("id");
        String userIdStr = snap.getString("userId");
        Long userId = null;
        if (userIdStr != null) {
            try {
                userId = Long.parseLong(userIdStr);
            } catch (NumberFormatException ignored) {}
        }
        Boolean disc = snap.getBoolean("discoverable");
        String fullName = snap.getString("userFullName");
        if (fullName == null || fullName.isBlank()) {
            fullName = snap.getString("displayName");
        }
        return new PhoneIdentity(
                id != null ? id : 1L,
                userId,
                snap.getString("phone"),
                disc == null || disc,
                fullName,
                toInstant(snap.getTimestamp("updatedAt"))
        );
    }

    private AbuseCounterRecord mapAbuseCounter(DocumentSnapshot snap) {
        Long id = snap.getLong("id");
        Long count = snap.getLong("lookupCount");
        Boolean flagged = snap.getBoolean("flagged");
        String dateStr = snap.getString("counterDate");
        return new AbuseCounterRecord(
                id != null ? id : 1L,
                snap.getLong("actorId"),
                dateStr != null ? LocalDate.parse(dateStr) : LocalDate.now(),
                count != null ? count.intValue() : 0,
                flagged != null && flagged
        );
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()) : null;
    }
}
