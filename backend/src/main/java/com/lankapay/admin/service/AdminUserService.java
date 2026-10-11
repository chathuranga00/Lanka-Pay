package com.lankapay.admin.service;

import com.lankapay.admin.repository.UserAdminSpecification;
import com.lankapay.admin.web.dto.AdminBankAccountSummary;
import com.lankapay.admin.web.dto.AdminUserDetailResponse;
import com.lankapay.admin.web.dto.AdminUserSummaryResponse;
import com.lankapay.admin.web.dto.UpdateUserStatusRequest;
import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.repository.BankAccountRepository;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for administrative user management.
 *
 * Security rules:
 * - Passwords, OTP hashes, and refresh tokens are strictly NEVER exposed.
 * - Impersonation is strictly disallowed.
 * - Every admin state change is audited with Propagation.REQUIRES_NEW.
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final UserRepository userRepository;
    private final BankAccountRepository bankAccountRepository;
    private final AuditService auditService;

    public AdminUserService(UserRepository userRepository,
                            BankAccountRepository bankAccountRepository,
                            AuditService auditService) {
        this.userRepository = userRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<AdminUserSummaryResponse> searchUsers(String phone, String nic, UserStatus status, UserRole role, Pageable pageable) {
        return userRepository.findAll(UserAdminSpecification.withFilters(phone, nic, status, role), pageable)
                .map(AdminUserSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found with id: " + userId));

        List<AdminBankAccountSummary> accounts = bankAccountRepository.findByUserIdOrderByLinkedAtDesc(userId)
                .stream()
                .map(AdminBankAccountSummary::from)
                .toList();

        return AdminUserDetailResponse.from(user, accounts);
    }

    @Transactional
    public AdminUserDetailResponse updateUserStatus(Long userId, UpdateUserStatusRequest request, Long adminUserId, String clientIp) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found with id: " + userId));

        if (request.status() == UserStatus.DELETED) {
            throw new ValidationException("INVALID_STATUS_TRANSITION",
                    "Admins cannot directly mark users as DELETED. Only ACTIVE and FROZEN status updates are supported.");
        }

        UserStatus oldStatus = user.getStatus();
        user.setStatus(request.status());
        userRepository.save(user);

        log.info("[ADMIN] User id={} status changed from {} to {} by admin id={}",
                userId, oldStatus, request.status(), adminUserId);

        auditService.log(adminUserId, "ADMIN_USER_STATUS_CHANGE", "USER", userId,
                "{\"from\":\"%s\",\"to\":\"%s\",\"reason\":\"%s\"}"
                        .formatted(oldStatus, request.status(), request.reason() != null ? request.reason() : ""), clientIp);

        List<AdminBankAccountSummary> accounts = bankAccountRepository.findByUserIdOrderByLinkedAtDesc(userId)
                .stream()
                .map(AdminBankAccountSummary::from)
                .toList();

        return AdminUserDetailResponse.from(user, accounts);
    }
}
