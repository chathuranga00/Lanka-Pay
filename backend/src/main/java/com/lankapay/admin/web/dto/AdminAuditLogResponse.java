package com.lankapay.admin.web.dto;

import com.lankapay.audit.domain.AuditLog;

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
    public static AdminAuditLogResponse from(AuditLog log) {
        return new AdminAuditLogResponse(
                log.getId(),
                log.getActorId(),
                log.getEventType(),
                log.getEntityType(),
                log.getEntityId(),
                log.getDetailJson(),
                log.getIpAddress(),
                log.getOccurredAt()
        );
    }
}
