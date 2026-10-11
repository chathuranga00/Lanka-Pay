package com.lankapay.identity.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * JPA entity for idn_users.
 *
 * Security rules:
 * - passwordHash stores BCrypt output only – never plaintext.
 * - phone and nicNumber must NEVER be logged in full.
 * - Soft-delete only: set status=DELETED, never physically remove rows.
 * - failedLoginAttempts and lockedUntil drive the login-lockout mechanism.
 */
@Entity
@Table(name = "idn_users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** E.164 format, e.g. +94771234567. Unique. Never log full value. */
    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /** Unique. Mask to first 4 chars + **** in all responses. */
    @Column(name = "nic_number", nullable = false, unique = true, length = 20)
    private String nicNumber;

    /** BCrypt hash (cost >= 12). Plaintext password is NEVER stored. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.CUSTOMER;

    /** Consecutive failed login attempts since last success. Reset on successful login. */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    /** Non-null while account is temporarily locked. Null when not locked. */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {}

    public User(String phone, String fullName, String nicNumber, String passwordHash) {
        this.phone        = phone;
        this.fullName     = fullName;
        this.nicNumber    = nicNumber;
        this.passwordHash = passwordHash;
    }

    // ── Getters ────────────────────────────────────────────────

    public Long getId()                    { return id; }
    public String getPhone()               { return phone; }
    public String getMaskedPhone()         { return com.lankapay.common.util.PhoneNumberNormalizer.mask(phone); }
    public String getFullName()            { return fullName; }
    public String getNicNumber()           { return nicNumber; }
    public String getPasswordHash()        { return passwordHash; }
    public UserStatus getStatus()          { return status; }
    public UserRole getRole()              { return role; }
    public int getFailedLoginAttempts()    { return failedLoginAttempts; }
    public Instant getLockedUntil()        { return lockedUntil; }
    public Instant getCreatedAt()          { return createdAt; }
    public Instant getUpdatedAt()          { return updatedAt; }

    // ── Setters ────────────────────────────────────────────────

    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setStatus(UserStatus status)         { this.status = status; }
    public void setRole(UserRole role)               { this.role = role; }
    public void setFullName(String fullName)         { this.fullName = fullName; }

    // ── Lockout helpers ────────────────────────────────────────

    public boolean isActive()  { return status == UserStatus.ACTIVE; }
    public boolean isFrozen()  { return status == UserStatus.FROZEN; }

    /** True if the account is currently within a lockout window. */
    public boolean isLockedOut() {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }

    /** Record a failed attempt. Returns the new attempt count. */
    public int incrementFailedAttempts() {
        return ++this.failedLoginAttempts;
    }

    /** Lock the account until the given instant. */
    public void lockUntil(Instant until) {
        this.lockedUntil = until;
    }

    /** Reset attempt counter and lockout after a successful login. */
    public void resetLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil         = null;
    }
}
