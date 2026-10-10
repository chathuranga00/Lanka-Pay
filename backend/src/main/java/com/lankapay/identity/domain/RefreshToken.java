package com.lankapay.identity.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * JPA entity for idn_refresh_tokens.
 *
 * Security rules:
 * - tokenHash stores SHA-256 of the raw token. The raw token is NEVER persisted.
 * - Tokens are revoked on logout, password/PIN change, or account freeze.
 */
@Entity
@Table(name = "idn_refresh_tokens",
       indexes = @Index(name = "idx_idn_refresh_tokens_user", columnList = "user_id"))
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 of the raw token string. Raw token never stored. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {}

    public RefreshToken(User user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public boolean isRevoked() { return revoked; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public boolean isValid() { return !revoked && !isExpired(); }

    public void revoke() { this.revoked = true; }
}
