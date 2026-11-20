package com.lankapay.admin.service;

import com.lankapay.admin.web.dto.AdminBankAccountSummary;
import com.lankapay.admin.web.dto.AdminUserDetailResponse;
import com.lankapay.admin.web.dto.AdminUserSummaryResponse;
import com.lankapay.admin.web.dto.UpdateUserStatusRequest;
import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for administrative user management.
 *
 * Security rules:
 * - Passwords, OTP hashes, and refresh tokens are strictly NEVER exposed.
 * - Impersonation is strictly disallowed.
 * - Every admin state change is audited.
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final UserStore userStore;
    private final LinkedAccountStore linkedAccountStore;
    private final AuditService auditService;

    public AdminUserService(UserStore userStore,
                            LinkedAccountStore linkedAccountStore,
                            AuditService auditService) {
        this.userStore = userStore;
        this.linkedAccountStore = linkedAccountStore;
        this.auditService = auditService;
    }

    public Page<AdminUserSummaryResponse> searchUsers(String phone, String nic, UserStatus status, UserRole role, Pageable pageable) {
        return userStore.searchUsers(phone, nic, status, role, pageable)
                .map(AdminUserSummaryResponse::from);
    }

    public AdminUserDetailResponse getUserDetail(Long userId) {
        UserAccount user = userStore.findUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found with id: " + userId));

        List<AdminBankAccountSummary> accounts = linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(userId)
                .stream()
                .map(AdminBankAccountSummary::from)
                .toList();

        return AdminUserDetailResponse.from(user, accounts);
    }

    public AdminUserDetailResponse updateUserStatus(Long userId, UpdateUserStatusRequest request, Long adminUserId, String clientIp) {
        UserAccount user = userStore.findUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found with id: " + userId));

        if (request.status() == UserStatus.DELETED) {
            throw new ValidationException("INVALID_STATUS_TRANSITION",
                    "Admins cannot directly mark users as DELETED. Only ACTIVE and FROZEN status updates are supported.");
        }

        UserStatus oldStatus = user.status();
        UserAccount updated = user.withStatus(request.status());
        userStore.saveUser(updated);

        log.info("[ADMIN] User id={} status changed from {} to {} by admin id={}",
                userId, oldStatus, request.status(), adminUserId);

        auditService.log(adminUserId, "ADMIN_USER_STATUS_CHANGE", "USER", userId,
                "{\"from\":\"%s\",\"to\":\"%s\",\"reason\":\"%s\"}"
                        .formatted(oldStatus, request.status(), request.reason() != null ? request.reason() : ""), clientIp);

        List<AdminBankAccountSummary> accounts = linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(userId)
                .stream()
                .map(AdminBankAccountSummary::from)
                .toList();

        return AdminUserDetailResponse.from(updated, accounts);
    }
}
