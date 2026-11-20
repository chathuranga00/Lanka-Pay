package com.lankapay.transfer.domain;

import java.time.Instant;

/**
 * Append-only event log for every transfer state transition.
 * Every status change is recorded here for auditability and debugging.
 *
 * Rules:
 * - detailJson must be sanitised — no PII, no full account numbers.
 */
public class TransactionEvent {

    private Long id;

    private Transfer transfer;

    private TransactionStatus fromStatus;

    private TransactionStatus toStatus;

    /** Machine-readable reason for the transition, e.g. "BANK_CONFIRMED", "TIMEOUT". */
    private String reason;

    /** Sanitised JSON detail — no PII, no full account numbers. */
    private String detailJson;

    private Instant occurredAt = Instant.now();

    public TransactionEvent() {}

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
