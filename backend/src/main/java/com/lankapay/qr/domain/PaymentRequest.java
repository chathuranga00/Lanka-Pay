package com.lankapay.qr.domain;

import com.lankapay.identity.domain.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain entity for pmt_payment_requests.
 *
 * Represents a payment request initiated by a requester to be fulfilled by a payer.
 * Can be linked to an opaque QR reference.
 */
public class PaymentRequest {

    private Long id;

    /** Opaque, unguessable public reference (e.g. "PREQ-550e8400..."). */
    private String reference;

    private User requester;

    /** Optional: specific designated payer, or null if open to anyone scanning. */
    private User payer;

    private BigDecimal amount;

    private String currency = "LKR";

    private String note;

    private PaymentRequestStatus status = PaymentRequestStatus.PENDING;

    private QrCode qrCode;

    private Long fulfilledTransferId;

    private Instant expiresAt;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    public PaymentRequest() {}

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
