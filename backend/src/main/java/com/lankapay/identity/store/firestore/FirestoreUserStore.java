package com.lankapay.identity.store.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.firestore.FirestoreTransactionHelper;
import com.lankapay.identity.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Firestore implementation of {@link UserStore}.
 *
 * Rules:
 * - Users collection: "users/{id}"
 * - Phone uniqueness: Transactionally reserve "phoneIdentities/{normalizedPhone}".
 *   Reject with 409 ConflictException if it already exists and is active.
 * - Sessions (refresh tokens): "sessions/{tokenHash}" where tokenHash is SHA-256.
 * - OTP challenges: "otpChallenges/{challengeId}" with transactional attempt counting and single-use consumption.
 * - OTP rate-limiting metadata: "otpMetadata/{userId_purpose}".
 * - Login attempt counters updated transactionally.
 */
@Component
public class FirestoreUserStore implements UserStore {

    private static final Logger log = LoggerFactory.getLogger(FirestoreUserStore.class);

    public static final String USERS_COLLECTION = "users";
    public static final String PHONE_IDENTITIES_COLLECTION = "phoneIdentities";
    public static final String SESSIONS_COLLECTION = "sessions";
    public static final String OTP_CHALLENGES_COLLECTION = "otpChallenges";
    public static final String OTP_METADATA_COLLECTION = "otpMetadata";
    public static final String SYSTEM_COUNTERS_COLLECTION = "system_counters";

    private final Firestore firestore;
    private final FirestoreTransactionHelper txHelper;
    private final AtomicLong fallbackIdGen = new AtomicLong(System.currentTimeMillis() % 100000000L);

    public FirestoreUserStore(Firestore firestore, FirestoreTransactionHelper txHelper) {
        this.firestore = firestore;
        this.txHelper = txHelper;
    }

    private Long nextId() {
        return fallbackIdGen.incrementAndGet();
    }

    // ── Users ──────────────────────────────────────────────────

    @Override
    public UserAccount saveUser(UserAccount user) {
        Long userId = user.id() != null ? user.id() : nextId();
        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(String.valueOf(userId));
        DocumentReference phoneRef = firestore.collection(PHONE_IDENTITIES_COLLECTION).document(user.phone());

        return txHelper.runTransaction(tx -> {
            DocumentSnapshot existingSnap = tx.get(userRef).get();
            DocumentSnapshot phoneSnap = tx.get(phoneRef).get();

            // Phone uniqueness check on initial creation or phone change
            if (phoneSnap.exists()) {
                String existingUserId = phoneSnap.getString("userId");
                Boolean active = phoneSnap.getBoolean("active");
                boolean isActive = active == null || active;
                if (isActive && (existingUserId == null || !existingUserId.equals(String.valueOf(userId)))) {
                    throw new ConflictException("PHONE_ALREADY_REGISTERED",
                            "An account with this phone number already exists");
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("id", userId);
            data.put("phone", user.phone());
            data.put("fullName", user.fullName());
            data.put("nicNumber", user.nicNumber());
            data.put("passwordHash", user.passwordHash());
            data.put("status", user.status().name());
            data.put("role", user.role().name());
            data.put("failedLoginAttempts", (long) user.failedLoginAttempts());
            data.put("lockedUntil", user.lockedUntil() != null ? Timestamp.ofTimeSecondsAndNanos(user.lockedUntil().getEpochSecond(), user.lockedUntil().getNano()) : null);
            data.put("updatedAt", FieldValue.serverTimestamp());

            if (!existingSnap.exists()) {
                data.put("createdAt", FieldValue.serverTimestamp());
            }

            tx.set(userRef, data, SetOptions.merge());

            // Update phone reservation in phoneIdentities
            Map<String, Object> phoneData = new HashMap<>();
            phoneData.put("phone", user.phone());
            phoneData.put("userId", String.valueOf(userId));
            phoneData.put("status", user.status().name());
            phoneData.put("active", user.status() != UserStatus.FROZEN);
            phoneData.put("userFullName", user.fullName());
            phoneData.put("discoverable", true);
            phoneData.put("claimedAt", FieldValue.serverTimestamp());
            tx.set(phoneRef, phoneData, SetOptions.merge());

            return new UserAccount(
                    userId,
                    user.phone(),
                    user.fullName(),
                    user.nicNumber(),
                    user.passwordHash(),
                    user.status(),
                    user.role(),
                    user.failedLoginAttempts(),
                    user.lockedUntil(),
                    user.createdAt() != null ? user.createdAt() : Instant.now(),
                    Instant.now()
            );
        });
    }

    @Override
    public Optional<UserAccount> findUserById(Long id) {
        if (id == null) return Optional.empty();
        try {
            DocumentSnapshot snap = firestore.collection(USERS_COLLECTION).document(String.valueOf(id)).get().get();
            if (!snap.exists()) return Optional.empty();
            return Optional.of(mapUser(snap));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<UserAccount> findUserByPhone(String phone) {
        if (phone == null || phone.isBlank()) return Optional.empty();
        try {
            QuerySnapshot snap = firestore.collection(USERS_COLLECTION)
                    .whereEqualTo("phone", phone)
                    .limit(1)
                    .get().get();
            if (snap.isEmpty()) return Optional.empty();
            return Optional.of(mapUser(snap.getDocuments().get(0)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<UserAccount> findUserByNicNumber(String nicNumber) {
        if (nicNumber == null || nicNumber.isBlank()) return Optional.empty();
        try {
            QuerySnapshot snap = firestore.collection(USERS_COLLECTION)
                    .whereEqualTo("nicNumber", nicNumber)
                    .limit(1)
                    .get().get();
            if (snap.isEmpty()) return Optional.empty();
            return Optional.of(mapUser(snap.getDocuments().get(0)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean existsByPhone(String phone) {
        return findUserByPhone(phone).isPresent();
    }

    @Override
    public boolean existsByNicNumber(String nicNumber) {
        return findUserByNicNumber(nicNumber).isPresent();
    }

    @Override
    public Optional<UserAccount> findByPhoneAndStatusNot(String phone, UserStatus status) {
        return findUserByPhone(phone).filter(u -> u.status() != status);
    }

    // ── OTP Challenges ─────────────────────────────────────────

    @Override
    public OtpRecord saveOtp(OtpRecord otp) {
        Long otpId = otp.id() != null ? otp.id() : nextId();
        DocumentReference otpRef = firestore.collection(OTP_CHALLENGES_COLLECTION).document(String.valueOf(otpId));

        Map<String, Object> data = new HashMap<>();
        data.put("id", otpId);
        data.put("userId", otp.userId());
        data.put("otpHash", otp.otpHash());
        data.put("purpose", otp.purpose().name());
        data.put("used", otp.used());
        data.put("attemptCount", 0L);
        data.put("expiresAt", toTimestamp(otp.expiresAt()));
        data.put("createdAt", toTimestamp(otp.createdAt() != null ? otp.createdAt() : Instant.now()));

        try {
            otpRef.set(data).get();
            return new OtpRecord(
                    otpId,
                    otp.userId(),
                    otp.otpHash(),
                    otp.purpose(),
                    otp.used(),
                    otp.expiresAt(),
                    otp.createdAt() != null ? otp.createdAt() : Instant.now()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<OtpRecord> findLatestValidOtp(Long userId, OtpPurpose purpose, Instant now) {
        try {
            QuerySnapshot snap = firestore.collection(OTP_CHALLENGES_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("purpose", purpose.name())
                    .whereEqualTo("used", false)
                    .get().get();

            return snap.getDocuments().stream()
                    .map(this::mapOtp)
                    .filter(o -> !o.isExpired())
                    .max(Comparator.comparing(OtpRecord::createdAt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int deleteExpiredOtpsBefore(Instant cutoff) {
        try {
            QuerySnapshot snap = firestore.collection(OTP_CHALLENGES_COLLECTION)
                    .whereLessThan("expiresAt", toTimestamp(cutoff))
                    .get().get();

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
    public Optional<OtpMetadataRecord> findOtpMetadata(Long userId, OtpPurpose purpose) {
        String docId = userId + "_" + purpose.name();
        try {
            DocumentSnapshot snap = firestore.collection(OTP_METADATA_COLLECTION).document(docId).get().get();
            if (!snap.exists()) return Optional.empty();
            return Optional.of(mapOtpMetadata(snap));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public OtpMetadataRecord saveOtpMetadata(OtpMetadataRecord metadata) {
        String docId = metadata.userId() + "_" + metadata.purpose().name();
        DocumentReference ref = firestore.collection(OTP_METADATA_COLLECTION).document(docId);

        Map<String, Object> data = new HashMap<>();
        data.put("id", metadata.id() != null ? metadata.id() : nextId());
        data.put("userId", metadata.userId());
        data.put("purpose", metadata.purpose().name());
        data.put("resendCooldownUntil", toTimestamp(metadata.resendCooldownUntil()));
        data.put("dailyOtpCount", (long) metadata.dailyOtpCount());
        data.put("dailyOtpDate", metadata.dailyOtpDate() != null ? metadata.dailyOtpDate().toString() : null);

        try {
            ref.set(data, SetOptions.merge()).get();
            return metadata;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    // ── Sessions (Refresh Tokens) ──────────────────────────────

    @Override
    public RefreshTokenRecord saveRefreshToken(RefreshTokenRecord token) {
        String docId = token.tokenHash();
        DocumentReference ref = firestore.collection(SESSIONS_COLLECTION).document(docId);

        Long tokenId = token.id() != null ? token.id() : nextId();
        Map<String, Object> data = new HashMap<>();
        data.put("id", tokenId);
        data.put("userId", token.userId());
        data.put("tokenHash", token.tokenHash());
        data.put("revoked", token.revoked());
        data.put("expiresAt", toTimestamp(token.expiresAt()));
        data.put("createdAt", toTimestamp(token.createdAt() != null ? token.createdAt() : Instant.now()));

        try {
            ref.set(data, SetOptions.merge()).get();
            return new RefreshTokenRecord(tokenId, token.userId(), token.tokenHash(), token.revoked(), token.expiresAt(), token.createdAt());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<RefreshTokenRecord> findRefreshTokenByHash(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) return Optional.empty();
        try {
            DocumentSnapshot snap = firestore.collection(SESSIONS_COLLECTION).document(tokenHash).get().get();
            if (!snap.exists()) return Optional.empty();
            return Optional.of(mapRefreshToken(snap));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int revokeAllRefreshTokensForUser(Long userId) {
        if (userId == null) return 0;
        try {
            QuerySnapshot snap = firestore.collection(SESSIONS_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("revoked", false)
                    .get().get();

            WriteBatch batch = firestore.batch();
            for (DocumentSnapshot doc : snap.getDocuments()) {
                batch.update(doc.getReference(), "revoked", true);
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

    // ── Transactional Login & OTP Verification ──────────────────

    @Override
    public UserAccount incrementFailedLoginAttempts(Long userId, int maxAttempts, Instant lockUntil) {
        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(String.valueOf(userId));
        return txHelper.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(userRef).get();
            if (!snap.exists()) {
                throw new com.lankapay.common.exception.ResourceNotFoundException("USER", userId);
            }
            UserAccount current = mapUser(snap);
            int newAttempts = current.failedLoginAttempts() + 1;
            Instant newLockedUntil = current.lockedUntil();
            if (newAttempts >= maxAttempts && lockUntil != null) {
                newLockedUntil = lockUntil;
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("failedLoginAttempts", (long) newAttempts);
            if (newLockedUntil != null) {
                updates.put("lockedUntil", toTimestamp(newLockedUntil));
            }
            updates.put("updatedAt", FieldValue.serverTimestamp());
            tx.set(userRef, updates, SetOptions.merge());

            return current.withFailedLoginAttempts(newAttempts).withLockedUntil(newLockedUntil);
        });
    }

    @Override
    public void resetFailedLoginAttempts(Long userId) {
        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(String.valueOf(userId));
        txHelper.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(userRef).get();
            if (snap.exists()) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("failedLoginAttempts", 0L);
                updates.put("lockedUntil", null);
                updates.put("updatedAt", FieldValue.serverTimestamp());
                tx.set(userRef, updates, SetOptions.merge());
            }
            return null;
        });
    }

    private enum OtpVerifyResult { SUCCESS, INVALID, MAX_ATTEMPTS, EXPIRED }

    @Override
    public boolean verifyAndConsumeOtp(Long challengeId, String plainOtp, java.util.function.BiPredicate<String, String> hashMatcher, int maxAttempts) {
        DocumentReference challengeRef = firestore.collection(OTP_CHALLENGES_COLLECTION).document(String.valueOf(challengeId));
        OtpVerifyResult result = txHelper.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(challengeRef).get();
            if (!snap.exists()) {
                return OtpVerifyResult.EXPIRED;
            }
            Boolean used = snap.getBoolean("used");
            if (Boolean.TRUE.equals(used)) {
                return OtpVerifyResult.EXPIRED;
            }
            Timestamp expTs = snap.getTimestamp("expiresAt");
            if (expTs != null && toInstant(expTs).isBefore(Instant.now())) {
                return OtpVerifyResult.EXPIRED;
            }

            Long attemptCount = snap.getLong("attemptCount");
            long attempts = attemptCount != null ? attemptCount : 0L;
            if (attempts >= maxAttempts) {
                return OtpVerifyResult.MAX_ATTEMPTS;
            }

            String hash = snap.getString("otpHash");
            if (!hashMatcher.test(plainOtp, hash)) {
                long newAttempts = attempts + 1;
                Map<String, Object> updates = new HashMap<>();
                updates.put("attemptCount", newAttempts);
                updates.put("updatedAt", FieldValue.serverTimestamp());
                tx.set(challengeRef, updates, SetOptions.merge());
                if (newAttempts >= maxAttempts) {
                    return OtpVerifyResult.MAX_ATTEMPTS;
                }
                return OtpVerifyResult.INVALID;
            }

            // OTP matched: mark used single-use consumption inside this transaction
            Map<String, Object> updates = new HashMap<>();
            updates.put("used", true);
            updates.put("consumedAt", FieldValue.serverTimestamp());
            updates.put("updatedAt", FieldValue.serverTimestamp());
            tx.set(challengeRef, updates, SetOptions.merge());
            return OtpVerifyResult.SUCCESS;
        });

        return switch (result) {
            case SUCCESS -> true;
            case INVALID -> throw com.lankapay.common.exception.OtpException.invalid();
            case MAX_ATTEMPTS -> throw com.lankapay.common.exception.OtpException.maxAttemptsReached();
            case EXPIRED -> throw com.lankapay.common.exception.OtpException.expired();
        };
    }

    @Override
    public Page<UserAccount> searchUsers(String phone, String nic, UserStatus status, UserRole role, Pageable pageable) {
        try {
            Query q = firestore.collection(USERS_COLLECTION);
            if (phone != null && !phone.isBlank()) {
                q = q.whereEqualTo("phone", phone);
            }
            if (nic != null && !nic.isBlank()) {
                q = q.whereEqualTo("nicNumber", nic);
            }
            if (status != null) {
                q = q.whereEqualTo("status", status.name());
            }
            if (role != null) {
                q = q.whereEqualTo("role", role.name());
            }

            QuerySnapshot snapshot = q.get().get();
            List<UserAccount> list = snapshot.getDocuments().stream()
                    .map(this::mapUser)
                    .sorted(Comparator.comparing(UserAccount::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();

            int start = (int) pageable.getOffset();
            if (start >= list.size()) {
                return new PageImpl<>(Collections.emptyList(), pageable, list.size());
            }
            int end = Math.min(start + pageable.getPageSize(), list.size());
            List<UserAccount> paged = list.subList(start, end);

            return new PageImpl<>(paged, pageable, list.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    // ── Mapping Helpers ────────────────────────────────────────

    private UserAccount mapUser(DocumentSnapshot doc) {
        Long id = doc.getLong("id");
        if (id == null) {
            try {
                id = Long.parseLong(doc.getId());
            } catch (NumberFormatException ignored) {
                id = 1L;
            }
        }
        Long attempts = doc.getLong("failedLoginAttempts");
        return new UserAccount(
                id,
                doc.getString("phone"),
                doc.getString("fullName"),
                doc.getString("nicNumber"),
                doc.getString("passwordHash"),
                UserStatus.valueOf(doc.getString("status")),
                UserRole.valueOf(doc.getString("role")),
                attempts != null ? attempts.intValue() : 0,
                toInstant(doc.getTimestamp("lockedUntil")),
                toInstant(doc.getTimestamp("createdAt")),
                toInstant(doc.getTimestamp("updatedAt"))
        );
    }

    private OtpRecord mapOtp(DocumentSnapshot doc) {
        Long id = doc.getLong("id");
        Boolean used = doc.getBoolean("used");
        return new OtpRecord(
                id != null ? id : 1L,
                doc.getLong("userId"),
                doc.getString("otpHash"),
                OtpPurpose.valueOf(doc.getString("purpose")),
                used != null && used,
                toInstant(doc.getTimestamp("expiresAt")),
                toInstant(doc.getTimestamp("createdAt"))
        );
    }

    private OtpMetadataRecord mapOtpMetadata(DocumentSnapshot doc) {
        Long count = doc.getLong("dailyOtpCount");
        String dateStr = doc.getString("dailyOtpDate");
        return new OtpMetadataRecord(
                doc.getLong("id"),
                doc.getLong("userId"),
                OtpPurpose.valueOf(doc.getString("purpose")),
                toInstant(doc.getTimestamp("resendCooldownUntil")),
                count != null ? count.intValue() : 0,
                dateStr != null ? LocalDate.parse(dateStr) : null
        );
    }

    private RefreshTokenRecord mapRefreshToken(DocumentSnapshot doc) {
        Boolean revoked = doc.getBoolean("revoked");
        return new RefreshTokenRecord(
                doc.getLong("id"),
                doc.getLong("userId"),
                doc.getString("tokenHash"),
                revoked != null && revoked,
                toInstant(doc.getTimestamp("expiresAt")),
                toInstant(doc.getTimestamp("createdAt"))
        );
    }

    @Override
    public void deleteUserByPhone(String phone) {
        if (phone == null || phone.isBlank()) return;
        findUserByPhone(phone).ifPresent(u -> {
            try {
                firestore.collection(USERS_COLLECTION).document(String.valueOf(u.id())).delete().get();
                firestore.collection(PHONE_IDENTITIES_COLLECTION).document(phone).delete().get();
                // Also delete with normalized variations just in case
                if (phone.startsWith("+94")) {
                    String local = "0" + phone.substring(3);
                    firestore.collection(PHONE_IDENTITIES_COLLECTION).document(local).delete().get();
                } else if (phone.startsWith("0")) {
                    String intl = "+94" + phone.substring(1);
                    firestore.collection(PHONE_IDENTITIES_COLLECTION).document(intl).delete().get();
                }
                log.info("Deleted user and phone identity for phone: {} (userId: {})", phone, u.id());
            } catch (Exception e) {
                log.warn("Failed to delete user documents for phone: {}", phone, e);
            }
        });
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano()) : null;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()) : null;
    }
}
