package com.lankapay.identity.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * JPA entity for idn_otps.
 *
 * Security rules:
 * - otpHash stores BCrypt hash only – the 6-digit plaintext is discarded after hashing.
 * - expires_at is enforced at the application level; a scheduled job also purges expired rows.
 */
@Entity
@Table(name = "idn_otps",
       indexes = @Index(name = "idx_idn_otps_lookup",
                        columnList = "user_id, purpose, used, expires_at"))
public class Otp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** BCrypt hash of the 6-digit OTP. Plaintext discarded after hashing. */
    @Column(name = "otp_hash", nullable = false, length = 255)
    private String otpHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OtpPurpose purpose;

    @Column(nullable = false)
    private boolean used = false;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Otp() {}

    public Otp(User user, String otpHash, OtpPurpose purpose, Instant expiresAt) {
        this.user = user;
        this.otpHash = otpHash;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getOtpHash() { return otpHash; }
    public OtpPurpose getPurpose() { return purpose; }
    public boolean isUsed() { return used; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }

    public void markUsed() { this.used = true; }
}
