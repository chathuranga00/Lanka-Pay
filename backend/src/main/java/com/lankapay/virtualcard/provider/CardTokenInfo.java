package com.lankapay.virtualcard.provider;

import java.time.Instant;

/**
 * Safe metadata the rest of the system may read about a token.
 * NEVER contains PAN, CVV, or reconstructable card data.
 */
public record CardTokenInfo(
        String       cardToken,
        String       last4,
        String       expMonth,
        String       expYear,
        String       brandLabel,
        Long         boundToUserId,
        CardScenario scenario,
        boolean      expired,
        boolean      simulated,
        Instant      tokenExpiresAt,
        Instant      issuedAt
) {}
