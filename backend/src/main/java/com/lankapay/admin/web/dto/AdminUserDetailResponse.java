package com.lankapay.admin.web.dto;

import com.lankapay.common.util.PhoneNumberNormalizer;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;

import java.time.Instant;
import java.util.List;

/**
 * Detailed user response for admins.
 * Strictly NEVER exposes passwords, OTPs, or authentication tokens.
 */
public record AdminUserDetailResponse(
        Long userId,
        String fullName,
        String maskedPhone,
        String maskedNic,
        UserStatus status,
        UserRole role,
        int failedLoginAttempts,
        Instant lockedUntil,
        List<AdminBankAccountSummary> linkedAccounts,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminUserDetailResponse from(User user, List<AdminBankAccountSummary> accounts) {
        return new AdminUserDetailResponse(
                user.getId(),
                user.getFullName(),
                PhoneNumberNormalizer.mask(user.getPhone()),
                maskNic(user.getNicNumber()),
                user.getStatus(),
                user.getRole(),
                user.getFailedLoginAttempts(),
                user.getLockedUntil(),
                accounts != null ? accounts : List.of(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private static String maskNic(String nic) {
        if (nic == null || nic.length() <= 4) return "****";
        return nic.substring(0, 4) + "****";
    }
}
