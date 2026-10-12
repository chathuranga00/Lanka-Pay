package com.lankapay.card.domain;

import java.time.Instant;

/**
 * Pure domain model for cards.
 *
 * Persisted in the Firestore "cards" collection.
 *
 * PCI-DSS & Anti-fraud Rules:
 * - Absolutely NO fields for PAN, CVV, or PIN exist in this entity or in Firestore.
 * - Only opaque {@code cardToken} and safe display {@code last4} are retained.
 * - Unique constraint: one active card per (userId, cardToken).
 * - Money limits are represented as minor units in LKR (e.g. 5,000,000 minor units = 50,000.00 LKR).
 */
public record Card(
        Long id,
        Long userId,
        Long linkedBankAccountId,
        String cardToken,
        String last4,
        Integer expMonth,
        Integer expYear,
        String brandLabel,
        String nickname,
        CardKind cardKind,
        CardStatus status,
        boolean onlinePaymentsEnabled,
        Long perPaymentLimitMinor,
        Long dailyLimitMinor,
        Instant createdAt,
        Instant updatedAt,
        Instant verifiedAt,
        Instant removedAt,
        Long version,
        String challengeRef,
        boolean simulated
) {
    public static final Long DEFAULT_PER_PAYMENT_LIMIT_MINOR = 5_000_000L;  // 50,000.00 LKR
    public static final Long DEFAULT_DAILY_LIMIT_MINOR       = 20_000_000L; // 200,000.00 LKR
    public static final Long MAX_PER_PAYMENT_LIMIT_MINOR     = 50_000_000L; // 500,000.00 LKR (system risk limit)
    public static final Long MAX_DAILY_LIMIT_MINOR           = 100_000_000L;// 1,000,000.00 LKR (system risk limit)

    public Card(Long userId, Long linkedBankAccountId, String cardToken, String last4,
                Integer expMonth, Integer expYear, String brandLabel, String nickname,
                CardKind cardKind, CardStatus status, String challengeRef) {
        this(
                null,
                userId,
                linkedBankAccountId,
                cardToken,
                last4,
                expMonth,
                expYear,
                brandLabel != null ? brandLabel : "TEST CARD",
                nickname != null ? nickname.trim() : null,
                cardKind,
                status,
                true,
                DEFAULT_PER_PAYMENT_LIMIT_MINOR,
                DEFAULT_DAILY_LIMIT_MINOR,
                Instant.now(),
                Instant.now(),
                status == CardStatus.ACTIVE ? Instant.now() : null,
                null,
                1L,
                challengeRef,
                true
        );
    }

    public boolean isActive() {
        return status == CardStatus.ACTIVE;
    }

    public boolean isPendingVerification() {
        return status == CardStatus.PENDING_VERIFICATION;
    }

    public boolean isFrozen() {
        return status == CardStatus.FROZEN;
    }

    public boolean isRemoved() {
        return status == CardStatus.REMOVED;
    }

    public boolean isExpired() {
        return status == CardStatus.EXPIRED;
    }

    public Card withId(Long id) {
        return new Card(id, userId, linkedBankAccountId, cardToken, last4, expMonth, expYear,
                brandLabel, nickname, cardKind, status, onlinePaymentsEnabled, perPaymentLimitMinor,
                dailyLimitMinor, createdAt, updatedAt, verifiedAt, removedAt, version, challengeRef, simulated);
    }

    public Card withStatus(CardStatus newStatus) {
        Instant now = Instant.now();
        Instant newVerified = (newStatus == CardStatus.ACTIVE && this.verifiedAt == null) ? now : this.verifiedAt;
        Instant newRemoved = (newStatus == CardStatus.REMOVED && this.removedAt == null) ? now : this.removedAt;
        boolean onlineEnabled = (newStatus == CardStatus.REMOVED) ? false : this.onlinePaymentsEnabled;
        return new Card(id, userId, linkedBankAccountId, cardToken, last4, expMonth, expYear,
                brandLabel, nickname, cardKind, newStatus, onlineEnabled, perPaymentLimitMinor,
                dailyLimitMinor, createdAt, now, newVerified, newRemoved, version != null ? version + 1 : 1L,
                challengeRef, simulated);
    }

    public Card withChallengeRef(String newChallengeRef) {
        return new Card(id, userId, linkedBankAccountId, cardToken, last4, expMonth, expYear,
                brandLabel, nickname, cardKind, status, onlinePaymentsEnabled, perPaymentLimitMinor,
                dailyLimitMinor, createdAt, Instant.now(), verifiedAt, removedAt,
                version != null ? version + 1 : 1L, newChallengeRef, simulated);
    }

    public Card withDetails(String newNickname, Long newPerPaymentLimitMinor, Long newDailyLimitMinor, Boolean newOnlineEnabled) {
        return new Card(
                id,
                userId,
                linkedBankAccountId,
                cardToken,
                last4,
                expMonth,
                expYear,
                brandLabel,
                newNickname != null ? newNickname.trim() : this.nickname,
                cardKind,
                status,
                newOnlineEnabled != null ? newOnlineEnabled : this.onlinePaymentsEnabled,
                newPerPaymentLimitMinor != null ? newPerPaymentLimitMinor : this.perPaymentLimitMinor,
                newDailyLimitMinor != null ? newDailyLimitMinor : this.dailyLimitMinor,
                createdAt,
                Instant.now(),
                verifiedAt,
                removedAt,
                version != null ? version + 1 : 1L,
                challengeRef,
                simulated
        );
    }
}
