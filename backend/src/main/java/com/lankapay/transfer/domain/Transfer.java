package com.lankapay.transfer.domain;

import com.lankapay.identity.domain.User;
import com.lankapay.qr.domain.QrCode;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain entity for transfers.
 *
 * Rules per section 9:
 * - status is managed via {@link #transitionTo(TransactionStatus, String, String)}
 *   which enforces {@link TransactionStateMachine} and emits a {@link TransactionEvent}.
 * - status is set to COMPLETED only after BankAdapter confirms both debit AND credit.
 * - Timeouts move to REQUIRES_RECONCILIATION, NEVER to FAILED.
 * - amount uses BigDecimal – never double or float.
 * - simulated is always true in this prototype.
 * - version enables optimistic locking / concurrency control.
 * - idempotencyKey prevents double submissions.
 */
public class Transfer {

    private Long id;

    /** UUID – used as an external reference. */
    private String reference;

    /** Client-provided idempotency key. */
    private String idempotencyKey;

    private User sender;

    private User receiver;

    private String senderBankCode;

    private String receiverBankCode;

    /** DECIMAL(15,2) – BigDecimal with scale 2. */
    private BigDecimal amount;

    private String currency = "LKR";

    private TransactionStatus status = TransactionStatus.PENDING;

    private String failureReason;

    /** Transaction reference returned by BankAdapter (always SIMULATED in prototype). */
    private String bankTxRef;

    /** Provider reference from the bank for reconciliation queries. */
    private String providerReference;

    /** Optional note from sender. */
    private String note;

    /** Always true in this prototype. */
    private boolean simulated = true;

    private TransferInitiatedVia initiatedVia = TransferInitiatedVia.APP;

    private QrCode qrCode;

    /** Optimistic locking / version control. */
    private Long version;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    public Transfer() {}

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
    public String getSenderBankCode()             { return senderBankCode; }
    public String getReceiverBankCode()           { return receiverBankCode; }
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

    public void setId(Long id)                           { this.id = id; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public void setSenderBankCode(String code)           { this.senderBankCode = code; }
    public void setReceiverBankCode(String code)         { this.receiverBankCode = code; }
    public void setNote(String note)                     { this.note = note; }
    public void setQrCode(QrCode qrCode)                 { this.qrCode = qrCode; }
    public void setProviderReference(String ref)         { this.providerReference = ref; }
    public void setBankTxRef(String ref)                 { this.bankTxRef = ref; }
    public void setStatus(TransactionStatus status)      { this.status = status; }
    public void setFailureReason(String failureReason)    { this.failureReason = failureReason; }
    public void setVersion(Long version)                 { this.version = version; }
    public void setCreatedAt(Instant createdAt)          { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt)          { this.updatedAt = updatedAt; }

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
