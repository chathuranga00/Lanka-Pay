package com.lankapay.admin.service;

import com.lankapay.admin.repository.AuditLogSpecification;
import com.lankapay.admin.web.dto.AdminAuditLogResponse;
import com.lankapay.audit.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AdminAuditService {

    private final AuditLogRepository auditLogRepository;

    public AdminAuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public Page<AdminAuditLogResponse> searchAuditLogs(Long actorId,
                                                       String eventType,
                                                       String entityType,
                                                       Long entityId,
                                                       Instant from,
                                                       Instant to,
                                                       Pageable pageable) {
        return auditLogRepository.findAll(AuditLogSpecification.withFilters(
                actorId, eventType, entityType, entityId, from, to), pageable)
                .map(AdminAuditLogResponse::from);
    }
}
