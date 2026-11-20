package com.lankapay.common.exception;

import java.time.Instant;

/**
 * Thrown when a user account is temporarily locked due to too many
 * failed login attempts. Maps to HTTP 429 (rate-limited / lockout).
 */
public class AccountLockedException extends LankaPayException {

    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super("ACCOUNT_LOCKED",
              "Account is temporarily locked. Try again after " + lockedUntil);
        this.lockedUntil = lockedUntil;
    }

    public Instant getLockedUntil() { return lockedUntil; }
}
