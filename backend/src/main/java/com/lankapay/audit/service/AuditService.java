package com.lankapay.audit.service;

import com.lankapay.audit.domain.AuditLog;
import com.lankapay.audit.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only audit service.
 *
 * Rules:
 * - This service exposes ONLY a log() method. No update or delete methods exist.
 * - detailJson must be sanitised by the caller before passing it here.
 * - Uses Propagation.REQUIRES_NEW so audit entries are committed even if the
 *   outer transaction rolls back (e.g. failed transfer still gets an audit row).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Persist an audit event.
     * Runs in its own transaction so failures in the caller do not suppress audit writes.
     *
     * @param actorId    ID of the user performing the action (null for system events)
     * @param eventType  e.g. "LOGIN_SUCCESS", "TRANSFER_COMPLETED"
     * @param entityType e.g. "USER", "TRANSFER"
     * @param entityId   Primary key of the affected entity
     * @param detailJson Sanitised JSON string – NO PII, no account numbers
     * @param ipAddress  Request IP address (IPv4 or IPv6)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long actorId, String eventType, String entityType,
                    Long entityId, String detailJson, String ipAddress) {

        AuditLog entry = new AuditLog(actorId, eventType, entityType,
                                      entityId, detailJson, ipAddress);
        repository.save(entry);
        log.debug("AUDIT event={} entity={}:{} actor={}", eventType, entityType, entityId, actorId);
    }
}
