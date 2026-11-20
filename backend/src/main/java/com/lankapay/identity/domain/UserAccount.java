package com.lankapay.identity.domain;

import java.time.Instant;

/**
 * Pure domain model representing a user account.
 * Contains no JPA or database-specific annotations.
 */
public record UserAccount(
        Long id,
        String phone,
        String fullName,
        String nicNumber,
        String passwordHash,
        UserStatus status,
        UserRole role,
        int failedLoginAttempts,
        Instant lockedUntil,
        Instant createdAt,
        Instant updatedAt
) {
    public UserAccount(String phone, String fullName, String nicNumber, String passwordHash) {
        this(null, phone, fullName, nicNumber, passwordHash, UserStatus.PENDING_VERIFICATION,
                UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now());
    }

    public boolean isLocked() {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public UserAccount withFailedLoginAttempts(int attempts) {
        return new UserAccount(id, phone, fullName, nicNumber, passwordHash, status, role,
                attempts, lockedUntil, createdAt, Instant.now());
    }

    public UserAccount withLockedUntil(Instant lockUntil) {
        return new UserAccount(id, phone, fullName, nicNumber, passwordHash, status, role,
                failedLoginAttempts, lockUntil, createdAt, Instant.now());
    }

    public UserAccount withStatus(UserStatus newStatus) {
        return new UserAccount(id, phone, fullName, nicNumber, passwordHash, newStatus, role,
                failedLoginAttempts, lockedUntil, createdAt, Instant.now());
    }

    public UserAccount withRole(UserRole newRole) {
        return new UserAccount(id, phone, fullName, nicNumber, passwordHash, status, newRole,
                failedLoginAttempts, lockedUntil, createdAt, Instant.now());
    }

    public UserAccount withPasswordHash(String newHash) {
        return new UserAccount(id, phone, fullName, nicNumber, newHash, status, role,
                failedLoginAttempts, lockedUntil, createdAt, Instant.now());
    }

    public User toUser() {
        User u = new User(phone, fullName, nicNumber, passwordHash);
        u.setId(id);
        u.setStatus(status);
        u.setRole(role);
        u.setFailedLoginAttempts(failedLoginAttempts);
        u.setLockedUntil(lockedUntil);
        u.setCreatedAt(createdAt);
        return u;
    }

    public static UserAccount fromUser(User u) {
        if (u == null) return null;
        return new UserAccount(
                u.getId(),
                u.getPhone(),
                u.getFullName(),
                u.getNicNumber(),
                u.getPasswordHash(),
                u.getStatus(),
                u.getRole(),
                u.getFailedLoginAttempts(),
                u.getLockedUntil(),
                u.getCreatedAt() != null ? u.getCreatedAt() : Instant.now(),
                u.getUpdatedAt() != null ? u.getUpdatedAt() : Instant.now()
        );
    }
}
