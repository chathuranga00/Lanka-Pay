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
 * Rules:
 * - status is set to COMPLETED only after BankAdapter confirms both debit AND credit.
 * - amount uses BigDecimal – never double or float.
 * - simulated is always true in this prototype.
 * - @Version enables optimistic locking to prevent duplicate processing.
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

    /** UUID – used as idempotency key. Unique constraint in DB. */
    @Column(nullable = false, unique = true, length = 36)
    private String reference;

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
    @Column(nullable = false, length = 20)
    private TransferStatus status = TransferStatus.PENDING;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    /** Transaction reference returned by BankAdapter (always SIMULATED in prototype). */
    @Column(name = "bank_tx_ref", length = 100)
    private String bankTxRef;

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
        this.currency = currency;
        this.initiatedVia = initiatedVia;
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public User getSender() { return sender; }
    public User getReceiver() { return receiver; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public TransferStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public String getBankTxRef() { return bankTxRef; }
    public boolean isSimulated() { return simulated; }
    public TransferInitiatedVia getInitiatedVia() { return initiatedVia; }
    public QrCode getQrCode() { return qrCode; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void complete(String bankTxRef) {
        this.status = TransferStatus.COMPLETED;
        this.bankTxRef = bankTxRef;
    }

    public void fail(String reason) {
        this.status = TransferStatus.FAILED;
        this.failureReason = reason;
    }

    public void setQrCode(QrCode qrCode) { this.qrCode = qrCode; }
}
