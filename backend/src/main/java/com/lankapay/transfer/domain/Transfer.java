package com.lankapay.transfer.domain;

import com.lankapay.identity.domain.User;
import com.lankapay.qr.domain.QrCode;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity for trf_transfers.
 *
 * Rules per section 9:
 * - status is managed via {@link #transitionTo(TransactionStatus, String, String)}
 *   which enforces {@link TransactionStateMachine} and emits a {@link TransactionEvent}.
 * - status is set to COMPLETED only after BankAdapter confirms both debit AND credit.
 * - Timeouts move to REQUIRES_RECONCILIATION, NEVER to FAILED.
 * - amount uses BigDecimal – never double or float.
 * - simulated is always true in this prototype.
 * - @Version enables optimistic locking to prevent concurrent status changes.
 * - idempotencyKey is a unique DB constraint to prevent double submissions.
 */
@Entity
@Table(name = "trf_transfers",
       indexes = {
           @Index(name = "idx_trf_transfers_sender",   columnList = "sender_id, created_at"),
           @Index(name = "idx_trf_transfers_receiver", columnList = "receiver_id, created_at"),
           @Index(name = "idx_trf_transfers_status",   columnList = "status")
       })
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UUID – used as an external reference. Unique constraint in DB. */
    @Column(nullable = false, unique = true, length = 36)
    private String reference;

    /** Client-provided idempotency key. Unique DB constraint. */
    @Column(name = "idempotency_key", unique = true, length = 64)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    /** DECIMAL(15,2) – BigDecimal with scale 2. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "LKR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    /** Transaction reference returned by BankAdapter (always SIMULATED in prototype). */
    @Column(name = "bank_tx_ref", length = 100)
    private String bankTxRef;

    /** Provider reference from the bank for reconciliation queries. */
    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    /** Optional note from sender. */
    @Column(name = "note", length = 255)
    private String note;

    /** Always true in this prototype. */
    @Column(nullable = false)
    private boolean simulated = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "initiated_via", nullable = false, length = 10)
    private TransferInitiatedVia initiatedVia = TransferInitiatedVia.APP;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qr_id")
    private QrCode qrCode;

    /** Optimistic locking – prevents concurrent status changes. */
    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transfer() {}

    public Transfer(String reference, User sender, User receiver,
                    BigDecimal amount, String currency, TransferInitiatedVia initiatedVia) {
        this.reference = reference;
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.currency = currency != null ? currency : "LKR";
        this.initiatedVia = initiatedVia != null ? initiatedVia : TransferInitiatedVia.APP;
        this.status = TransactionStatus.PENDING;
    }

    // ── Getters ────────────────────────────────────────────────

    public Long getId()                           { return id; }
    public String getReference()                  { return reference; }
    public String getIdempotencyKey()             { return idempotencyKey; }
    public User getSender()                       { return sender; }
    public User getReceiver()                     { return receiver; }
    public BigDecimal getAmount()                 { return amount; }
    public String getCurrency()                   { return currency; }
    public TransactionStatus getStatus()          { return status; }
    public String getFailureReason()              { return failureReason; }
    public String getBankTxRef()                  { return bankTxRef; }
    public String getProviderReference()          { return providerReference; }
    public String getNote()                       { return note; }
    public boolean isSimulated()                  { return simulated; }
    public TransferInitiatedVia getInitiatedVia() { return initiatedVia; }
    public QrCode getQrCode()                     { return qrCode; }
    public Long getVersion()                      { return version; }
    public Instant getCreatedAt()                 { return createdAt; }
    public Instant getUpdatedAt()                 { return updatedAt; }

    // ── Setters ────────────────────────────────────────────────

    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public void setNote(String note)                     { this.note = note; }
    public void setQrCode(QrCode qrCode)                 { this.qrCode = qrCode; }
    public void setProviderReference(String ref)         { this.providerReference = ref; }
    public void setBankTxRef(String ref)                 { this.bankTxRef = ref; }

    // ── State Machine transitions ─────────────────────────────

    /**
     * Transition to a new status via the state machine.
     * The caller must persist the returned TransactionEvent.
     *
     * @param target     Desired target status
     * @param reason     Machine-readable reason (e.g. "BANK_CONFIRMED", "TIMEOUT")
     * @param detailJson Sanitised JSON detail — no PII
     * @return A new TransactionEvent recording this transition
     * @throws IllegalStateException if the transition is not legal
     */
    public TransactionEvent transitionTo(TransactionStatus target, String reason, String detailJson) {
        TransactionStatus from = this.status;
        TransactionStateMachine.validateTransition(from, target);
        this.status = target;
        return new TransactionEvent(this, from, target, reason, detailJson);
    }

    /**
     * Mark transfer as PROCESSING when sent to BankAdapter.
     */
    public TransactionEvent startProcessing(String reason) {
        return transitionTo(TransactionStatus.PROCESSING, reason, null);
    }

    /**
     * Complete the transfer — only after BankAdapter confirms.
     */
    public TransactionEvent complete(String bankTxRef, String providerRef) {
        this.bankTxRef = bankTxRef;
        this.providerReference = providerRef;
        return transitionTo(TransactionStatus.COMPLETED, "BANK_CONFIRMED",
                "{\"bankTxRef\":\"" + (bankTxRef != null ? bankTxRef : "") + "\"}");
    }

    /**
     * Fail the transfer — only for confirmed permanent failures.
     */
    public TransactionEvent fail(String reason) {
        this.failureReason = reason;
        return transitionTo(TransactionStatus.FAILED, reason, null);
    }

    /**
     * Move to REQUIRES_RECONCILIATION on timeout — NEVER to FAILED.
     */
    public TransactionEvent requiresReconciliation(String providerRef) {
        this.providerReference = providerRef;
        return transitionTo(TransactionStatus.REQUIRES_RECONCILIATION, "TIMEOUT",
                "{\"providerReference\":\"" + (providerRef != null ? providerRef : "") + "\"}");
    }

    /**
     * Initiate reversal flow.
     */
    public TransactionEvent requestReversal(String reason) {
        return transitionTo(TransactionStatus.REVERSAL_PENDING, "REVERSAL_REQUESTED", reason);
    }

    /**
     * Confirm reversal completed by adapter.
     */
    public TransactionEvent reversed(String providerRef) {
        this.providerReference = providerRef;
        return transitionTo(TransactionStatus.REVERSED, "REVERSAL_CONFIRMED",
                "{\"providerReference\":\"" + (providerRef != null ? providerRef : "") + "\"}");
    }
}
