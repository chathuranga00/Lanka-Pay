package com.lankapay.qr.domain;

import com.lankapay.identity.domain.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity for qr_codes.
 * A QR code represents a payment request from a merchant to be fulfilled by a payer.
 *
 * Status transitions: ACTIVE → USED (paid) | EXPIRED (TTL elapsed) | CANCELLED
 */
@Entity
@Table(name = "qr_codes",
       indexes = {
           @Index(name = "idx_qr_codes_lookup",   columnList = "qr_id, status, expires_at"),
           @Index(name = "idx_qr_codes_merchant", columnList = "merchant_id")
       })
public class QrCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UUID embedded in the QR payload. Unique. */
    @Column(name = "qr_id", nullable = false, unique = true, length = 36)
    private String qrId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private User merchant;

    /** DECIMAL(15,2). Server re-fetches this from DB – client-provided amount is ignored. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "LKR";

    @Column(length = 255)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QrStatus status = QrStatus.ACTIVE;

    /** Default 15 minutes after creation. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected QrCode() {}

    public QrCode(String qrId, User merchant, BigDecimal amount, String currency,
                  String note, Instant expiresAt) {
        this.qrId = qrId;
        this.merchant = merchant;
        this.amount = amount;
        this.currency = currency;
        this.note = note;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public String getQrId() { return qrId; }
    public User getMerchant() { return merchant; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getNote() { return note; }
    public QrStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public boolean isPayable() { return status == QrStatus.ACTIVE && !isExpired(); }

    public void markUsed() { this.status = QrStatus.USED; }
    public void markExpired() { this.status = QrStatus.EXPIRED; }
    public void cancel() { this.status = QrStatus.CANCELLED; }
}
