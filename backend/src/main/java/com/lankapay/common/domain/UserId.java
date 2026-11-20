package com.lankapay.common.domain;

import java.util.Objects;

/**
 * Typed wrapper for a user's primary key.
 * Prevents accidental confusion between user IDs and other BIGINT IDs.
 */
public record UserId(Long value) {

    public UserId {
        Objects.requireNonNull(value, "UserId value must not be null");
        if (value <= 0) throw new IllegalArgumentException("UserId must be positive");
    }

    public static UserId of(Long value) {
        return new UserId(value);
    }

    @Override
    public String toString() {
        return "UserId(%d)".formatted(value);
    }
}
