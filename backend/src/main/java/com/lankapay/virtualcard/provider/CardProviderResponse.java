package com.lankapay.virtualcard.provider;

import java.time.Instant;

/** Generic success/failure response for non-payment vault operations. */
public record CardProviderResponse(
        boolean  success,
        String   message,
        boolean  simulated,
        Instant  respondedAt
) {
    public static CardProviderResponse ok(String message) {
        return new CardProviderResponse(true, message, true, Instant.now());
    }

    public static CardProviderResponse failure(String message) {
        return new CardProviderResponse(false, message, true, Instant.now());
    }
}
