package com.lankapay.virtualcard.provider;

import java.time.Instant;

/**
 * Returned by authorize, capture, void, refund, queryPayment.
 * Never contains PAN, CVV, or full card number.
 */
public record CardAuthResponse(
        boolean        approved,
        String         authCode,         // vault-side authorization code; null on decline
        String         cardToken,
        String         last4,            // safe for receipts
        CardAuthStatus status,
        String         declineReason,    // null on approval
        CardScenario   scenario,         // SIMULATED only
        boolean        simulated,
        Instant        respondedAt
) {
    public static CardAuthResponse approved(String authCode, String token, String last4, CardScenario sc) {
        return new CardAuthResponse(true, authCode, token, last4,
                CardAuthStatus.APPROVED, null, sc, true, Instant.now());
    }

    public static CardAuthResponse declined(String reason, String token, String last4, CardScenario sc) {
        return new CardAuthResponse(false, null, token, last4,
                CardAuthStatus.DECLINED, reason, sc, true, Instant.now());
    }

    public static CardAuthResponse timeout(String token, String last4, CardScenario sc) {
        return new CardAuthResponse(false, null, token, last4,
                CardAuthStatus.TIMEOUT, "Provider timeout", sc, true, Instant.now());
    }

    public static CardAuthResponse pendingChallenge(String token, String last4) {
        return new CardAuthResponse(false, null, token, last4,
                CardAuthStatus.PENDING_CHALLENGE, "OTP challenge required",
                CardScenario.REQUIRES_OTP_CHALLENGE, true, Instant.now());
    }
}
