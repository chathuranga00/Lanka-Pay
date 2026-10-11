package com.lankapay.admin.web.dto;

import com.lankapay.common.util.PhoneNumberNormalizer;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;

import java.time.Instant;

/**
 * Privacy-safe user summary for admin views.
 * Strictly NEVER exposes passwords, OTPs, or authentication tokens.
 */
public record AdminUserSummaryResponse(
        Long userId,
        String fullName,
        String maskedPhone,
        String maskedNic,
        UserStatus status,
        UserRole role,
        Instant createdAt
) {
    public static AdminUserSummaryResponse from(User user) {
        if (user == null) return null;
        return new AdminUserSummaryResponse(
                user.getId(),
                user.getFullName(),
                PhoneNumberNormalizer.mask(user.getPhone()),
                maskNic(user.getNicNumber()),
                user.getStatus(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

    public static AdminUserSummaryResponse from(com.lankapay.identity.domain.UserAccount user) {
        if (user == null) return null;
        return new AdminUserSummaryResponse(
                user.id(),
                user.fullName(),
                PhoneNumberNormalizer.mask(user.phone()),
                maskNic(user.nicNumber()),
                user.status(),
                user.role(),
                user.createdAt()
        );
    }

    private static String maskNic(String nic) {
        if (nic == null || nic.length() <= 4) return "****";
        return nic.substring(0, 4) + "****";
    }
}
