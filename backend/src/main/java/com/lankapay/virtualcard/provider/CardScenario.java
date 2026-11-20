package com.lankapay.virtualcard.provider;

/**
 * Deterministic test scenarios for simulated card operations.
 * Triggered by the last-4 digits of the test card number (see docs/test-cards.md).
 * All scenarios are SIMULATED — no real money moves.
 */
public enum CardScenario {
    ALWAYS_APPROVED,
    INSUFFICIENT_FUNDS,
    CARD_EXPIRED,
    REQUIRES_OTP_CHALLENGE,
    PROVIDER_TIMEOUT_BUT_CHARGED,
    DELAYED_CONFIRMATION,
    HARD_DECLINE,
    LATER_REVERSAL
}
