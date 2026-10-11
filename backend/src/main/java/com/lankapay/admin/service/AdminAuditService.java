package com.lankapay.admin.service;

import com.lankapay.admin.web.dto.AdminAuditLogResponse;
import com.lankapay.audit.domain.AuditStore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AdminAuditService {

    private final AuditStore auditStore;

    public AdminAuditService(AuditStore auditStore) {
        this.auditStore = auditStore;
    }

    public Page<AdminAuditLogResponse> searchAuditLogs(Long actorId,
                                                       String eventType,
                                                       String entityType,
                                                       Long entityId,
                                                       Instant from,
                                                       Instant to,
                                                       Pageable pageable) {
        if (actorId != null) {
            return auditStore.findByActorId(actorId, pageable).map(AdminAuditLogResponse::from);
        }
        return auditStore.findAll(pageable).map(AdminAuditLogResponse::from);
    }
}
