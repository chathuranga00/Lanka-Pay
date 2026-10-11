package com.lankapay.audit.repository;

import com.lankapay.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByActorIdOrderByOccurredAtDesc(Long actorId, Pageable pageable);

    Page<AuditLog> findByEventTypeOrderByOccurredAtDesc(String eventType, Pageable pageable);

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            String entityType, Long entityId, Pageable pageable);

    /** Admin: full log, newest first. */
    Page<AuditLog> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
