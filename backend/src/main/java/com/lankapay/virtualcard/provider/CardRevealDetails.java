package com.lankapay.virtualcard.provider;

/**
 * Sensitive card details revealed for virtual cards only.
 * Handled in memory only — NEVER logged, NEVER persisted in Firestore.
 */
public record CardRevealDetails(
        String  cardToken,
        String  pan,
        String  cvv,
        String  expMonth,
        String  expYear,
        String  brandLabel,
        int     expiresInSeconds,
        boolean simulated
) {}
