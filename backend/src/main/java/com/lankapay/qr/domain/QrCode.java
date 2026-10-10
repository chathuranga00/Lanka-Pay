package com.lankapay.qr.domain;

import com.lankapay.identity.domain.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for qr_codes.
 *
 * Rules per section 10:
 * - Opaque, unguessable references (no account data, no PII, no tokens in the QR).
 * - Static QR (reusable, no fixed amount or expiry, oneTimeUse = false).
 * - Dynamic QR (amount + expiry + unique reference, oneTimeUse = true).
 * - Expiry enforcement: expired QR cannot be resolved or paid.
 * - One-time use enforcement for dynamic requests.
 */
@Entity
@Table(name = "qr_codes",
       indexes = {
           @Index(name = "idx_qr_codes_lookup",   columnList = "reference, status, expires_at"),
           @Index(name = "idx_qr_codes_merchant", columnList = "merchant_id")
       })
public class QrCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Opaque, unguessable unique public reference (e.g. "QREF-550e8400..."). */
    @Column(name = "reference", nullable = false, unique = true, length = 64)
    private String reference;

    /** Legacy UUID column for backwards compatibility with qrId. */
    @Column(name = "qr_id", nullable = false, unique = true, length = 36)
    private String qrId;

    @Enumerated(EnumType.STRING)
    @Column(name = "qr_type", nullable = false, length = 20)
    private QrType qrType = QrType.DYNAMIC;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private User merchant;

    /** DECIMAL(15,2). Nullable for STATIC QR codes; mandatory for DYNAMIC QR. */
    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "LKR";

    @Column(length = 255)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QrStatus status = QrStatus.ACTIVE;

    /** Nullable for STATIC QR codes; mandatory for DYNAMIC QR codes. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** True for dynamic one-time QR; false for reusable static QR. */
    @Column(name = "one_time_use", nullable = false)
    private boolean oneTimeUse = true;

    /** Completely opaque payload string: e.g. "LANKAPAY:QR:QREF-xxxx". No PII or credentials. */
    @Column(name = "payload", length = 255)
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected QrCode() {}

    /**
     * Create a dynamic QR code with fixed amount, note, and expiry.
     */
    public static QrCode createDynamic(User merchant, BigDecimal amount, String currency,
                                       String note, Instant expiresAt) {
        String uuid = UUID.randomUUID().toString();
        String ref = "QREF-" + uuid;
        QrCode qr = new QrCode();
        qr.reference = ref;
        qr.qrId = uuid;
        qr.qrType = QrType.DYNAMIC;
        qr.merchant = merchant;
        qr.amount = amount;
        qr.currency = currency != null ? currency : "LKR";
        qr.note = note;
        qr.status = QrStatus.ACTIVE;
        qr.expiresAt = expiresAt;
        qr.oneTimeUse = true;
        qr.payload = "LANKAPAY:QR:" + ref;
        return qr;
    }

    /**
     * Create a static QR code (reusable, no fixed amount or expiry).
     */
    public static QrCode createStatic(User merchant, String currency, String note) {
        String uuid = UUID.randomUUID().toString();
        String ref = "QREF-STATIC-" + uuid;
        QrCode qr = new QrCode();
        qr.reference = ref;
        qr.qrId = uuid;
        qr.qrType = QrType.STATIC;
        qr.merchant = merchant;
        qr.amount = null;
        qr.currency = currency != null ? currency : "LKR";
        qr.note = note;
        qr.status = QrStatus.ACTIVE;
        qr.expiresAt = null;
        qr.oneTimeUse = false;
        qr.payload = "LANKAPAY:QR:" + ref;
        return qr;
    }

    // ── Getters ────────────────────────────────────────────────

    public Long getId()               { return id; }
    public String getReference()      { return reference; }
    public String getQrId()           { return qrId; }
    public QrType getQrType()         { return qrType; }
    public User getMerchant()         { return merchant; }
    public BigDecimal getAmount()     { return amount; }
    public String getCurrency()       { return currency; }
    public String getNote()           { return note; }
    public QrStatus getStatus()       { return status; }
    public Instant getExpiresAt()     { return expiresAt; }
    public boolean isOneTimeUse()     { return oneTimeUse; }
    public String getPayload()        { return payload; }
    public Instant getCreatedAt()     { return createdAt; }

    // ── Business checks & transitions ──────────────────────────

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isPayable() {
        if (status != QrStatus.ACTIVE) return false;
        if (isExpired()) {
            markExpired();
            return false;
        }
        return true;
    }

    public void markUsed() {
        this.status = QrStatus.USED;
    }

    public void markExpired() {
        this.status = QrStatus.EXPIRED;
    }

    public void cancel() {
        this.status = QrStatus.CANCELLED;
    }
}
