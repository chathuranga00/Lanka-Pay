package com.lankapay.audit.domain;

import java.time.Instant;

/**
 * Pure domain model representing an immutable audit log entry.
 * Contains no JPA or database-specific annotations.
 */
public record AuditRecord(
        Long id,
        Long actorId,
        String eventType,
        String entityType,
        Long entityId,
        String detailJson,
        String ipAddress,
        Instant occurredAt
) {
    public AuditRecord(Long actorId, String eventType, String entityType,
                       Long entityId, String detailJson, String ipAddress) {
        this(null, actorId, eventType, entityType, entityId, detailJson, ipAddress, Instant.now());
    }
}
