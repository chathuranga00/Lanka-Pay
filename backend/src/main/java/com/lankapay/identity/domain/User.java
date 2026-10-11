package com.lankapay.identity.domain;

import java.time.Instant;

/**
 * Domain model for User identity and authentication principal.
 *
 * Security rules:
 * - passwordHash stores BCrypt output only – never plaintext.
 * - phone and nicNumber must NEVER be logged in full.
 * - Soft-delete only: set status=DELETED, never physically remove records.
 * - failedLoginAttempts and lockedUntil drive the login-lockout mechanism.
 */
public class User {

    private Long id;

    /** E.164 format, e.g. +94771234567. Unique. Never log full value. */
    private String phone;

    private String fullName;

    /** Unique. Mask to first 4 chars + **** in all responses. */
    private String nicNumber;

    /** BCrypt hash (cost >= 12). Plaintext password is NEVER stored. */
    private String passwordHash;

    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    private UserRole role = UserRole.CUSTOMER;

    /** Consecutive failed login attempts since last success. Reset on successful login. */
    private int failedLoginAttempts = 0;

    /** Non-null while account is temporarily locked. Null when not locked. */
    private Instant lockedUntil;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    public User() {}

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

    public void setId(Long id)                       { this.id = id; }
    public void setPhone(String phone)               { this.phone = phone; }
    public void setNicNumber(String nicNumber)       { this.nicNumber = nicNumber; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setStatus(UserStatus status)         { this.status = status; }
    public void setRole(UserRole role)               { this.role = role; }
    public void setFullName(String fullName)         { this.fullName = fullName; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }
    public void setLockedUntil(Instant lockedUntil)  { this.lockedUntil = lockedUntil; }
    public void setCreatedAt(Instant createdAt)      { this.createdAt = createdAt; }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        if (id != null && user.id != null) return id.equals(user.id);
        return java.util.Objects.equals(phone, user.phone);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : (phone != null ? phone.hashCode() : 0);
    }
}
