package com.lankapay.registry.domain;

import java.time.Instant;

/**
 * Pure domain model representing a registered phone payment identity.
 * Contains no JPA or database-specific annotations.
 */
public record PhoneIdentity(
        Long id,
        Long userId,
        String phone,
        boolean discoverable,
        String fullName,
        Instant updatedAt
) {
    public PhoneIdentity(Long userId, String phone, boolean discoverable) {
        this(null, userId, phone, discoverable, null, Instant.now());
    }

    public PhoneIdentity(Long userId, String phone, boolean discoverable, String fullName) {
        this(null, userId, phone, discoverable, fullName, Instant.now());
    }

    public PhoneIdentity withDiscoverable(boolean newDiscoverable) {
        return new PhoneIdentity(id, userId, phone, newDiscoverable, fullName, Instant.now());
    }
}
