package com.lankapay.transfer.domain;

import com.lankapay.card.domain.CardKind;
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
 * - status is set to COMPLETED only after BankAdapter confirms both debit AND credit
 *   or CardProvider confirms capture.
 * - Timeouts move to REQUIRES_RECONCILIATION, NEVER to FAILED.
 * - amount uses BigDecimal – never double or float.
 * - simulated is always true in this prototype.
 * - version enables optimistic locking / concurrency control.
 * - idempotencyKey prevents double submissions.
 * - fundingType can be ACCOUNT or CARD. Card PAN/CVV is NEVER stored.
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

    /** Transaction reference returned by BankAdapter or Card authCode. */
    private String bankTxRef;

    /** Provider reference from the bank or card vault for reconciliation queries. */
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

    // ── Funding Source Snapshot ──────────────────────────────
    private FundingType fundingType = FundingType.ACCOUNT;
    private Long cardId;
    private String cardLast4;
    private CardKind cardKind;
    private BigDecimal fee = BigDecimal.ZERO;
    private BigDecimal totalAmount;
    private String challengeRef;
    private int challengeAttempts = 0;

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
        this.totalAmount = amount;
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

    public FundingType getFundingType()           { return fundingType; }
    public Long getCardId()                       { return cardId; }
    public String getCardLast4()                  { return cardLast4; }
    public CardKind getCardKind()                 { return cardKind; }
    public BigDecimal getFee()                    { return fee != null ? fee : BigDecimal.ZERO; }
    public BigDecimal getTotalAmount()            { return totalAmount != null ? totalAmount : amount; }
    public String getChallengeRef()               { return challengeRef; }
    public int getChallengeAttempts()             { return challengeAttempts; }

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

    public void setFundingType(FundingType fundingType)   { this.fundingType = fundingType != null ? fundingType : FundingType.ACCOUNT; }
    public void setCardId(Long cardId)                   { this.cardId = cardId; }
    public void setCardLast4(String cardLast4)           { this.cardLast4 = cardLast4; }
    public void setCardKind(CardKind cardKind)           { this.cardKind = cardKind; }
    public void setFee(BigDecimal fee)                   { this.fee = fee; }
    public void setTotalAmount(BigDecimal totalAmount)   { this.totalAmount = totalAmount; }
    public void setChallengeRef(String challengeRef)     { this.challengeRef = challengeRef; }
    public void setChallengeAttempts(int attempts)       { this.challengeAttempts = attempts; }

    // ── State Machine transitions ─────────────────────────────

    /**
     * Transition to a new status via the state machine.
     * The caller must persist the returned TransactionEvent.
     */
    public TransactionEvent transitionTo(TransactionStatus target, String reason, String detailJson) {
        TransactionStatus from = this.status;
        TransactionStateMachine.validateTransition(from, target);
        this.status = target;
        return new TransactionEvent(this, from, target, reason, detailJson);
    }

    /**
     * Move to CHALLENGE_REQUIRED for card OTP verification.
     */
    public TransactionEvent requireChallenge(String challengeRef) {
        this.challengeRef = challengeRef;
        return transitionTo(TransactionStatus.CHALLENGE_REQUIRED, "CHALLENGE_REQUIRED",
                "{\"challengeRef\":\"" + (challengeRef != null ? challengeRef : "") + "\"}");
    }

    /**
     * Mark transfer as PROCESSING when sent to BankAdapter.
     */
    public TransactionEvent startProcessing(String reason) {
        return transitionTo(TransactionStatus.PROCESSING, reason, null);
    }

    /**
     * Complete the transfer — only after BankAdapter or CardProvider confirms.
     */
    public TransactionEvent complete(String bankTxRef, String providerRef) {
        this.bankTxRef = bankTxRef;
        this.providerReference = providerRef;
        return transitionTo(TransactionStatus.COMPLETED, "BANK_CONFIRMED",
                "{\"bankTxRef\":\"" + (bankTxRef != null ? bankTxRef : "") + "\"}");
    }

    /**
     * Record card capture and complete.
     */
    public TransactionEvent recordCardCaptured(String authCode, String providerRef) {
        this.bankTxRef = authCode;
        this.providerReference = providerRef != null ? providerRef : authCode;
        return transitionTo(TransactionStatus.COMPLETED, "CARD_CAPTURED",
                "{\"authCode\":\"" + (authCode != null ? authCode : "") + "\",\"cardLast4\":\"" + (cardLast4 != null ? cardLast4 : "") + "\"}");
    }

    public TransactionEvent recordCardCaptured(String authCode) {
        return recordCardCaptured(authCode, authCode);
    }

    /**
     * Record card authorization (moves to PROCESSING).
     */
    public TransactionEvent recordCardAuthorized(String authCode) {
        this.bankTxRef = authCode;
        return transitionTo(TransactionStatus.PROCESSING, "CARD_AUTHORIZED",
                "{\"authCode\":\"" + (authCode != null ? authCode : "") + "\",\"cardLast4\":\"" + (cardLast4 != null ? cardLast4 : "") + "\"}");
    }

    /**
     * Record card decline (moves to FAILED).
     */
    public TransactionEvent recordCardDeclined(String reason) {
        this.failureReason = reason;
        return transitionTo(TransactionStatus.FAILED, "CARD_DECLINED",
                "{\"reason\":\"" + (reason != null ? reason : "") + "\",\"cardLast4\":\"" + (cardLast4 != null ? cardLast4 : "") + "\"}");
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

    public TransactionEvent recordCardVoided(String providerRef) {
        this.providerReference = providerRef;
        return transitionTo(TransactionStatus.REVERSED, "CARD_VOIDED",
                "{\"providerReference\":\"" + (providerRef != null ? providerRef : "") + "\"}");
    }

    public TransactionEvent recordCardRefunded(String providerRef) {
        this.providerReference = providerRef;
        return transitionTo(TransactionStatus.REVERSED, "CARD_REFUNDED",
                "{\"providerReference\":\"" + (providerRef != null ? providerRef : "") + "\"}");
    }
}
