package com.lankapay.card.store.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.lankapay.card.domain.Card;
import com.lankapay.card.domain.CardKind;
import com.lankapay.card.domain.CardStatus;
import com.lankapay.card.domain.CardStore;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.firestore.FirestoreTransactionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Firestore persistence implementation for the "cards" collection.
 *
 * Enforces:
 * - Atomic transactions via {@link FirestoreTransactionHelper}.
 * - Optimistic concurrency control via {@code version} field.
 * - Unique constraint: one active card per (userId, cardToken).
 * - PCI-DSS compliance: NEVER stores PAN, CVV, or PIN.
 * - Explicit consent recording in the "consents" collection.
 */
@Component
public class FirestoreCardStore implements CardStore {

    private static final Logger log = LoggerFactory.getLogger(FirestoreCardStore.class);

    public static final String CARDS_COLLECTION    = "cards";
    public static final String CONSENTS_COLLECTION = "consents";

    private final Firestore firestore;
    private final FirestoreTransactionHelper txHelper;
    private final AtomicLong cardIdGen    = new AtomicLong(System.currentTimeMillis() % 100000000L);
    private final AtomicLong consentIdGen = new AtomicLong(System.currentTimeMillis() % 100000000L);

    public FirestoreCardStore(Firestore firestore, FirestoreTransactionHelper txHelper) {
        this.firestore = firestore;
        this.txHelper  = txHelper;
    }

    private Long nextCardId() {
        return cardIdGen.incrementAndGet();
    }

    private Long nextConsentId() {
        return consentIdGen.incrementAndGet();
    }

    @Override
    public Card save(Card card) {
        return txHelper.runTransaction(tx -> {
            Long cardId = card.id();
            boolean isNew = (cardId == null);

            if (isNew) {
                cardId = nextCardId();

                // 1. Unique constraint check: one active card per (userId, cardToken)
                Query query = firestore.collection(CARDS_COLLECTION)
                        .whereEqualTo("userId", card.userId())
                        .whereEqualTo("cardToken", card.cardToken());
                QuerySnapshot existingSnapshots = tx.get(query).get();

                for (DocumentSnapshot doc : existingSnapshots.getDocuments()) {
                    String statusStr = doc.getString("status");
                    if (statusStr != null && !CardStatus.REMOVED.name().equals(statusStr)) {
                        throw new ConflictException("DUPLICATE_CARD",
                                "An active card already exists with this token for this user");
                    }
                }
            }

            DocumentReference docRef = firestore.collection(CARDS_COLLECTION).document(String.valueOf(cardId));
            DocumentSnapshot existingSnap = tx.get(docRef).get();

            long nextVersion = 1L;
            Instant createdAt = card.createdAt() != null ? card.createdAt() : Instant.now();

            if (existingSnap.exists()) {
                Long currentVersion = existingSnap.getLong("version");
                if (currentVersion != null) {
                    if (card.version() != null && !card.version().equals(currentVersion)) {
                        throw new ConflictException("OPTIMISTIC_LOCK_FAILURE",
                                "Card was updated concurrently. Please refresh and retry.");
                    }
                    nextVersion = currentVersion + 1;
                }
                Timestamp existingCreated = existingSnap.getTimestamp("createdAt");
                if (existingCreated != null) {
                    createdAt = toInstant(existingCreated);
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("id", cardId);
            data.put("userId", card.userId());
            data.put("linkedBankAccountId", card.linkedBankAccountId());
            data.put("cardToken", card.cardToken());
            data.put("last4", card.last4());
            data.put("expMonth", card.expMonth());
            data.put("expYear", card.expYear());
            data.put("brandLabel", card.brandLabel());
            data.put("nickname", card.nickname());
            data.put("cardKind", card.cardKind().name());
            data.put("status", card.status().name());
            data.put("onlinePaymentsEnabled", card.onlinePaymentsEnabled());
            data.put("perPaymentLimitMinor", card.perPaymentLimitMinor());
            data.put("dailyLimitMinor", card.dailyLimitMinor());
            data.put("createdAt", toTimestamp(createdAt));
            data.put("updatedAt", toTimestamp(Instant.now()));
            data.put("verifiedAt", toTimestamp(card.verifiedAt()));
            data.put("removedAt", toTimestamp(card.removedAt()));
            data.put("version", nextVersion);
            data.put("challengeRef", card.challengeRef());
            data.put("simulated", card.simulated());

            // Write all changes inside transaction
            tx.set(docRef, data, SetOptions.merge());

            return new Card(
                    cardId,
                    card.userId(),
                    card.linkedBankAccountId(),
                    card.cardToken(),
                    card.last4(),
                    card.expMonth(),
                    card.expYear(),
                    card.brandLabel(),
                    card.nickname(),
                    card.cardKind(),
                    card.status(),
                    card.onlinePaymentsEnabled(),
                    card.perPaymentLimitMinor(),
                    card.dailyLimitMinor(),
                    createdAt,
                    Instant.now(),
                    card.verifiedAt(),
                    card.removedAt(),
                    nextVersion,
                    card.challengeRef(),
                    card.simulated()
            );
        });
    }

    @Override
    public Optional<Card> findById(Long id) {
        if (id == null) return Optional.empty();
        try {
            DocumentSnapshot snap = firestore.collection(CARDS_COLLECTION)
                    .document(String.valueOf(id))
                    .get()
                    .get();
            if (!snap.exists()) return Optional.empty();
            return Optional.of(toDomain(snap));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore read interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Firestore read failed", e);
        }
    }

    @Override
    public Optional<Card> findByIdAndUserId(Long id, Long userId) {
        return findById(id).filter(c -> c.userId().equals(userId));
    }

    @Override
    public List<Card> findAllByUserId(Long userId) {
        if (userId == null) return Collections.emptyList();
        try {
            QuerySnapshot snaps = firestore.collection(CARDS_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .get()
                    .get();

            List<Card> cards = new ArrayList<>();
            for (DocumentSnapshot snap : snaps.getDocuments()) {
                cards.add(toDomain(snap));
            }
            // Sort by createdAt descending
            cards.sort((a, b) -> {
                if (a.createdAt() == null || b.createdAt() == null) return 0;
                return b.createdAt().compareTo(a.createdAt());
            });
            return cards;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore query interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Firestore query failed", e);
        }
    }

    @Override
    public Optional<Card> findActiveByUserIdAndToken(Long userId, String cardToken) {
        if (userId == null || cardToken == null) return Optional.empty();
        try {
            QuerySnapshot snaps = firestore.collection(CARDS_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("cardToken", cardToken)
                    .get()
                    .get();

            for (DocumentSnapshot snap : snaps.getDocuments()) {
                String status = snap.getString("status");
                if (status != null && !CardStatus.REMOVED.name().equals(status)) {
                    return Optional.of(toDomain(snap));
                }
            }
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore query interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Firestore query failed", e);
        }
    }

    @Override
    public List<Card> findCardsToProcessExpiration(int year, int month) {
        try {
            QuerySnapshot snaps = firestore.collection(CARDS_COLLECTION)
                    .get()
                    .get();

            List<Card> expiredCandidates = new ArrayList<>();
            for (DocumentSnapshot snap : snaps.getDocuments()) {
                Card card = toDomain(snap);
                if (card.status() == CardStatus.ACTIVE
                        || card.status() == CardStatus.FROZEN
                        || card.status() == CardStatus.PENDING_VERIFICATION) {
                    if (card.expYear() != null && card.expMonth() != null) {
                        int cardYear = card.expYear();
                        // Normalize 2-digit years to 4-digit
                        if (cardYear < 100) cardYear += 2000;
                        int checkYear = year < 100 ? year + 2000 : year;

                        if (cardYear < checkYear || (cardYear == checkYear && card.expMonth() < month)) {
                            expiredCandidates.add(card);
                        }
                    }
                }
            }
            return expiredCandidates;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore query interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Firestore query failed", e);
        }
    }

    @Override
    public void saveConsent(Long userId, Long cardId, String consentType, boolean consentGiven, String ip) {
        Long consentId = nextConsentId();
        DocumentReference ref = firestore.collection(CONSENTS_COLLECTION).document(String.valueOf(consentId));

        Map<String, Object> data = new HashMap<>();
        data.put("id", consentId);
        data.put("userId", userId);
        data.put("cardId", cardId);
        data.put("consentType", consentType);
        data.put("purpose", consentType);
        data.put("consentGiven", consentGiven);
        data.put("status", consentGiven ? "GRANTED" : "REVOKED");
        data.put("consentIp", ip);
        data.put("consentedAt", FieldValue.serverTimestamp());

        try {
            ref.set(data, SetOptions.merge()).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore consent write interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Firestore consent write failed", e);
        }
    }

    private Card toDomain(DocumentSnapshot snap) {
        Long id = snap.getLong("id");
        if (id == null) {
            try {
                id = Long.parseLong(snap.getId());
            } catch (NumberFormatException ignored) {}
        }

        String kindStr = snap.getString("cardKind");
        CardKind kind = kindStr != null ? CardKind.valueOf(kindStr) : CardKind.LINKED;

        String statusStr = snap.getString("status");
        CardStatus status = statusStr != null ? CardStatus.valueOf(statusStr) : CardStatus.PENDING_VERIFICATION;

        Boolean onlineEnabled = snap.getBoolean("onlinePaymentsEnabled");
        Boolean simulated = snap.getBoolean("simulated");

        Long perPayment = snap.getLong("perPaymentLimitMinor");
        Long dailyLimit = snap.getLong("dailyLimitMinor");

        Long expMonthLong = snap.getLong("expMonth");
        Long expYearLong  = snap.getLong("expYear");

        return new Card(
                id,
                snap.getLong("userId"),
                snap.getLong("linkedBankAccountId"),
                snap.getString("cardToken"),
                snap.getString("last4"),
                expMonthLong != null ? expMonthLong.intValue() : null,
                expYearLong != null ? expYearLong.intValue() : null,
                snap.getString("brandLabel"),
                snap.getString("nickname"),
                kind,
                status,
                onlineEnabled != null ? onlineEnabled : true,
                perPayment != null ? perPayment : Card.DEFAULT_PER_PAYMENT_LIMIT_MINOR,
                dailyLimit != null ? dailyLimit : Card.DEFAULT_DAILY_LIMIT_MINOR,
                toInstant(snap.getTimestamp("createdAt")),
                toInstant(snap.getTimestamp("updatedAt")),
                toInstant(snap.getTimestamp("verifiedAt")),
                toInstant(snap.getTimestamp("removedAt")),
                snap.getLong("version"),
                snap.getString("challengeRef"),
                simulated != null ? simulated : true
        );
    }

    private Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toDate().toInstant() : null;
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano()) : null;
    }
}
