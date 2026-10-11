package com.lankapay.audit.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Storage port interface for audit log operations.
 * Decouples domain services from specific persistence technologies.
 */
public interface AuditStore {

    AuditRecord save(AuditRecord record);

    Optional<AuditRecord> findById(Long id);

    Page<AuditRecord> findAll(Pageable pageable);

    Page<AuditRecord> findByActorId(Long actorId, Pageable pageable);
}
