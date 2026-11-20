package com.lankapay.virtualcard.provider;

import java.time.Instant;

/**
 * Returned by {@link CardProvider#tokenize}.
 *
 * NEVER contains PAN, CVV, or full expiry in a way that reconstructs the card number.
 * {@code cardToken} is the only identifier the rest of the system ever sees.
 */
public record TokenizeResponse(
        String  cardToken,        // opaque UUID — bound to requestingUserId
        String  last4,            // safe to display
        String  expMonth,         // safe to display
        String  expYear,          // safe to display
        String  brandLabel,       // always "TEST CARD"
        boolean simulated,        // always true
        Instant tokenExpiresAt    // 15 minutes; token must be linked before this
) {}
