package com.lankapay.audit.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * JPA entity for aud_audit_log.
 *
 * Rules:
 * - Append-only: AuditService exposes NO update or delete methods.
 * - detailJson must be sanitised before insert: no PII, no full account numbers,
 *   no OTP values, no plaintext passwords.
 * - actorId has no FK constraint intentionally – audit rows must outlive users.
 * - ipAddress stores IPv4 or IPv6 (max 45 chars).
 */
@Entity
@Table(name = "aud_audit_log",
       indexes = {
           @Index(name = "idx_aud_audit_log_actor",       columnList = "actor_id, event_type, occurred_at"),
           @Index(name = "idx_aud_audit_log_entity",      columnList = "entity_type, entity_id"),
           @Index(name = "idx_aud_audit_log_occurred_at", columnList = "occurred_at")
       })
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nullable – system-generated events have no human actor. */
    @Column(name = "actor_id")
    private Long actorId;

    /** e.g. LOGIN_SUCCESS, TRANSFER_COMPLETED, ADMIN_FREEZE_USER */
    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    /** e.g. USER, TRANSFER, QR_CODE, BANK_ACCOUNT */
    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    /** Sanitised JSON – no PII, no account numbers, no secrets. */
    @Column(name = "detail_json", columnDefinition = "TEXT")
    private String detailJson;

    /** IPv4 or IPv6 string. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditLog() {}

    public AuditLog(Long actorId, String eventType, String entityType,
                    Long entityId, String detailJson, String ipAddress) {
        this.actorId = actorId;
        this.eventType = eventType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.detailJson = detailJson;
        this.ipAddress = ipAddress;
    }

    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getEventType() { return eventType; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getDetailJson() { return detailJson; }
    public String getIpAddress() { return ipAddress; }
    public Instant getOccurredAt() { return occurredAt; }
}
