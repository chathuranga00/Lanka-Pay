package com.lankapay.simvault.internal;

import java.time.Instant;

/**
 * An issued token binding a card to a user for a short TTL window.
 *
 * ISOLATION: package-private — never exported outside com.lankapay.simvault.
 */
record VaultToken(
        String  token,
        String  last4,
        String  expMonth,
        String  expYear,
        Long    boundToUserId,
        VaultScenario scenario,
        Instant issuedAt,
        Instant expiresAt
) {
    boolean isExpired()  { return Instant.now().isAfter(expiresAt); }
    boolean isBoundTo(Long userId) { return boundToUserId != null && boundToUserId.equals(userId); }
}
