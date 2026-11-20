package com.lankapay.simvault.internal;

import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ResourceNotFoundException;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for short-lived vault tokens.
 *
 * ISOLATION: package-private — never exported outside com.lankapay.simvault.
 *
 * Production would persist tokens in a short-TTL Redis or Firestore collection.
 * For a university prototype, an in-memory map is acceptable.
 */
final class VaultTokenStore {

    private final ConcurrentHashMap<String, VaultToken> tokens = new ConcurrentHashMap<>();
    private final long ttlMinutes;

    VaultTokenStore(long ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }

    VaultToken issue(String token, String last4, String expMonth, String expYear,
                     Long userId, VaultScenario scenario) {
        VaultToken vt = new VaultToken(
                token, last4, expMonth, expYear, userId, scenario,
                Instant.now(), Instant.now().plusSeconds(ttlMinutes * 60));
        tokens.put(token, vt);
        return vt;
    }

    VaultToken requireValid(String token, Long requestingUserId) {
        VaultToken vt = tokens.get(token);
        if (vt == null) {
            throw new ResourceNotFoundException("CARD_TOKEN_NOT_FOUND",
                    "Card token not found or already used");
        }
        if (vt.isExpired()) {
            tokens.remove(token);
            throw new ResourceNotFoundException("CARD_TOKEN_EXPIRED",
                    "Card token has expired. Please tokenize again.");
        }
        if (!vt.isBoundTo(requestingUserId)) {
            throw new AuthorizationException("CARD_TOKEN_WRONG_USER",
                    "Card token does not belong to this user");
        }
        return vt;
    }

    Optional<VaultToken> find(String token) {
        return Optional.ofNullable(tokens.get(token)).filter(t -> !t.isExpired());
    }

    void invalidate(String token) {
        tokens.remove(token);
    }

    /** Evict all expired tokens (call from a scheduled job or before each lookup). */
    void evictExpired() {
        tokens.entrySet().removeIf(e -> e.getValue().isExpired());
    }
}
