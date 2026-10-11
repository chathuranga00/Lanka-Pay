package com.lankapay.admin.web.dto;

import com.lankapay.audit.domain.AuditRecord;

import java.time.Instant;

public record AdminAuditLogResponse(
        Long id,
        Long actorId,
        String eventType,
        String entityType,
        Long entityId,
        String detailJson,
        String ipAddress,
        Instant occurredAt
) {
    public static AdminAuditLogResponse from(AuditRecord log) {
        if (log == null) return null;
        return new AdminAuditLogResponse(
                log.id(),
                log.actorId(),
                log.eventType(),
                log.entityType(),
                log.entityId(),
                log.detailJson(),
                log.ipAddress(),
                log.occurredAt()
        );
    }
}
