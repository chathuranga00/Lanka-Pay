package com.lankapay.transfer.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Append-only event log for every transfer state transition.
 * Every status change is recorded here for auditability and debugging.
 *
 * Table: trf_transaction_events
 * Rules:
 * - NEVER update or delete rows in this table.
 * - detailJson must be sanitised — no PII, no full account numbers.
 */
@Entity
@Table(name = "trf_transaction_events",
       indexes = {
           @Index(name = "idx_trf_tx_events_transfer", columnList = "transfer_id, occurred_at"),
           @Index(name = "idx_trf_tx_events_status", columnList = "to_status")
       })
public class TransactionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private Transfer transfer;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private TransactionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private TransactionStatus toStatus;

    /** Machine-readable reason for the transition, e.g. "BANK_CONFIRMED", "TIMEOUT". */
    @Column(name = "reason", length = 100)
    private String reason;

    /** Sanitised JSON detail — no PII, no full account numbers. */
    @Column(name = "detail_json", columnDefinition = "TEXT")
    private String detailJson;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected TransactionEvent() {}

    public TransactionEvent(Transfer transfer, TransactionStatus fromStatus,
                            TransactionStatus toStatus, String reason, String detailJson) {
        this.transfer   = transfer;
        this.fromStatus = fromStatus;
        this.toStatus   = toStatus;
        this.reason     = reason;
        this.detailJson = detailJson;
    }

    public Long getId()                      { return id; }
    public Transfer getTransfer()            { return transfer; }
    public TransactionStatus getFromStatus() { return fromStatus; }
    public TransactionStatus getToStatus()   { return toStatus; }
    public String getReason()                { return reason; }
    public String getDetailJson()            { return detailJson; }
    public Instant getOccurredAt()           { return occurredAt; }
}
