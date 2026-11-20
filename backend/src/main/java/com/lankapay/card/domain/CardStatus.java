package com.lankapay.card.domain;

/**
 * Lifecycle status of a card:
 * PENDING_VERIFICATION - Challenge OTP has been initiated, awaiting verification.
 * ACTIVE - Verified and active for payments.
 * FROZEN - Temporarily suspended by the user.
 * EXPIRED - Past expiration date.
 * REMOVED - Soft-deleted / removed by the user.
 */
public enum CardStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    FROZEN,
    EXPIRED,
    REMOVED
}
