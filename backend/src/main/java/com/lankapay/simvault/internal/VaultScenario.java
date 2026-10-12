package com.lankapay.simvault.internal;

import com.lankapay.virtualcard.provider.CardScenario;

/**
 * Maps the last-4 digits of a seeded test card to its deterministic scenario.
 *
 * ⚠ SIMULATED ONLY — these scenarios never apply to any real card network.
 * All PANs start with 99 (not a real BIN range).
 */
enum VaultScenario {
    ALWAYS_APPROVED         (CardScenario.ALWAYS_APPROVED,              "0010"),
    INSUFFICIENT_FUNDS      (CardScenario.INSUFFICIENT_FUNDS,           "0028"),
    CARD_EXPIRED            (CardScenario.CARD_EXPIRED,                 "0069"),
    REQUIRES_OTP_CHALLENGE  (CardScenario.REQUIRES_OTP_CHALLENGE,       "0119"),
    PROVIDER_TIMEOUT        (CardScenario.PROVIDER_TIMEOUT_BUT_CHARGED, "0994"),
    DELAYED_CONFIRMATION    (CardScenario.DELAYED_CONFIRMATION,         "1232"),
    HARD_DECLINE            (CardScenario.HARD_DECLINE,                 "2347"),
    LATER_REVERSAL          (CardScenario.LATER_REVERSAL,               "3451");

    final CardScenario cardScenario;
    final String last4;

    VaultScenario(CardScenario cardScenario, String last4) {
        this.cardScenario = cardScenario;
        this.last4        = last4;
    }

    static VaultScenario forLast4(String last4) {
        for (VaultScenario vs : values()) {
            if (vs.last4.equals(last4)) return vs;
        }
        return ALWAYS_APPROVED;
    }
}
