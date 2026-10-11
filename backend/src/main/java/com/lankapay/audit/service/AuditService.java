package com.lankapay.audit.service;

import com.lankapay.audit.domain.AuditRecord;
import com.lankapay.audit.domain.AuditStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Append-only audit service.
 *
 * Rules:
 * - This service exposes ONLY a log() method. No update or delete methods exist.
 * - detailJson must be sanitised by the caller before passing it here.
 * - Audit entries are committed to persistent store.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditStore auditStore;

    public AuditService(AuditStore auditStore) {
        this.auditStore = auditStore;
    }

    /**
     * Persist an audit event.
     *
     * @param actorId    ID of the user performing the action (null for system events)
     * @param eventType  e.g. "LOGIN_SUCCESS", "TRANSFER_COMPLETED"
     * @param entityType e.g. "USER", "TRANSFER"
     * @param entityId   Primary key of the affected entity
     * @param detailJson Sanitised JSON string – NO PII, no account numbers
     * @param ipAddress  Request IP address (IPv4 or IPv6)
     */
    public void log(Long actorId, String eventType, String entityType,
                    Long entityId, String detailJson, String ipAddress) {

        AuditRecord entry = new AuditRecord(
                actorId, eventType, entityType, entityId, detailJson, ipAddress);
        auditStore.save(entry);
        log.debug("AUDIT event={} entity={}:{} actor={}", eventType, entityType, entityId, actorId);
    }
}
