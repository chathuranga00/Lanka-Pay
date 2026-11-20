package com.lankapay.common.domain;

import java.util.Objects;

/**
 * Typed wrapper for a bank account's primary key.
 * Prevents accidental confusion between account IDs and other BIGINT IDs.
 */
public record AccountId(Long value) {

    public AccountId {
        Objects.requireNonNull(value, "AccountId value must not be null");
        if (value <= 0) throw new IllegalArgumentException("AccountId must be positive");
    }

    public static AccountId of(Long value) {
        return new AccountId(value);
    }

    @Override
    public String toString() {
        return "AccountId(%d)".formatted(value);
    }
}
