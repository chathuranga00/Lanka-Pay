package com.lankapay.qr.domain;

import com.lankapay.identity.domain.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for pmt_payment_requests.
 *
 * Represents a payment request initiated by a requester to be fulfilled by a payer.
 * Can be linked to an opaque QR reference.
 */
@Entity
@Table(name = "pmt_payment_requests",
       indexes = {
           @Index(name = "idx_pmt_req_requester", columnList = "requester_id"),
           @Index(name = "idx_pmt_req_status",    columnList = "status")
       })
public class PaymentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Opaque, unguessable public reference (e.g. "PREQ-550e8400..."). */
    @Column(nullable = false, unique = true, length = 64)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    /** Optional: specific designated payer, or null if open to anyone scanning. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_id")
    private User payer;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "LKR";

    @Column(length = 255)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentRequestStatus status = PaymentRequestStatus.PENDING;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "qr_code_id")
    private QrCode qrCode;

    @Column(name = "fulfilled_transfer_id")
    private Long fulfilledTransferId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentRequest() {}

    public PaymentRequest(User requester, User payer, BigDecimal amount, String currency,
                          String note, Instant expiresAt, QrCode qrCode) {
        this.reference = "PREQ-" + UUID.randomUUID();
        this.requester = requester;
        this.payer = payer;
        this.amount = amount;
        this.currency = currency != null ? currency : "LKR";
        this.note = note;
        this.expiresAt = expiresAt;
        this.qrCode = qrCode;
        this.status = PaymentRequestStatus.PENDING;
    }

    // ── Getters ────────────────────────────────────────────────

    public Long getId()                      { return id; }
    public String getReference()             { return reference; }
    public User getRequester()               { return requester; }
    public User getPayer()                   { return payer; }
    public BigDecimal getAmount()            { return amount; }
    public String getCurrency()              { return currency; }
    public String getNote()                  { return note; }
    public PaymentRequestStatus getStatus()  { return status; }
    public QrCode getQrCode()                { return qrCode; }
    public Long getFulfilledTransferId()     { return fulfilledTransferId; }
    public Instant getExpiresAt()            { return expiresAt; }
    public Instant getCreatedAt()            { return createdAt; }
    public Instant getUpdatedAt()            { return updatedAt; }

    // ── Business checks & transitions ──────────────────────────

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isPayable() {
        if (status != PaymentRequestStatus.PENDING) return false;
        if (isExpired()) {
            this.status = PaymentRequestStatus.EXPIRED;
            return false;
        }
        return true;
    }

    public void markCompleted(Long transferId) {
        this.status = PaymentRequestStatus.COMPLETED;
        this.fulfilledTransferId = transferId;
        if (this.qrCode != null) {
            this.qrCode.markUsed();
        }
    }

    public void cancel() {
        this.status = PaymentRequestStatus.CANCELLED;
        if (this.qrCode != null) {
            this.qrCode.cancel();
        }
    }
}
