package com.lankapay.admin.web;

import com.lankapay.admin.domain.FraudAlertSeverity;
import com.lankapay.admin.domain.FraudAlertStatus;
import com.lankapay.admin.service.*;
import com.lankapay.admin.web.dto.*;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.web.dto.ReconcileResultResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Controller for administrative capabilities (ADMIN role only).
 *
 * Rules:
 * - ADMIN role only (@PreAuthorize("hasRole('ADMIN')")).
 * - Strictly NEVER exposes passwords, OTPs, or authentication tokens.
 * - Impersonation is prohibited.
 * - Admins cannot edit bank-confirmed transactions (COMPLETED, REVERSED).
 * - Every state change or reconciliation trigger is audited.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminUserService adminUserService;
    private final AdminBankService adminBankService;
    private final AdminTransferService adminTransferService;
    private final RiskManagementService riskManagementService;
    private final AdminAuditService adminAuditService;

    public AdminController(AdminUserService adminUserService,
                           AdminBankService adminBankService,
                           AdminTransferService adminTransferService,
                           RiskManagementService riskManagementService,
                           AdminAuditService adminAuditService) {
        this.adminUserService = adminUserService;
        this.adminBankService = adminBankService;
        this.adminTransferService = adminTransferService;
        this.riskManagementService = riskManagementService;
        this.adminAuditService = adminAuditService;
    }

    // ── 1. User Management ─────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserSummaryResponse>> searchUsers(
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String nic,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) UserRole role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(adminUserService.searchUsers(phone, nic, status, role, pageable));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<AdminUserDetailResponse> getUserDetail(@PathVariable Long userId) {
        return ResponseEntity.ok(adminUserService.getUserDetail(userId));
    }

    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<AdminUserDetailResponse> updateUserStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @AuthenticationPrincipal Long adminUserId,
            HttpServletRequest httpRequest
    ) {
        String clientIp = getClientIp(httpRequest);
        return ResponseEntity.ok(adminUserService.updateUserStatus(userId, request, adminUserId, clientIp));
    }

    // ── 2. Bank Config & Integration Health ────────────────────

    @GetMapping("/banks")
    public ResponseEntity<List<BankConfigResponse>> getBanks() {
        return ResponseEntity.ok(adminBankService.getAllBanks());
    }

    @PatchMapping("/banks/{bankCode}")
    public ResponseEntity<BankConfigResponse> updateBankConfig(
            @PathVariable String bankCode,
            @Valid @RequestBody UpdateBankConfigRequest request,
            @AuthenticationPrincipal Long adminUserId,
            HttpServletRequest httpRequest
    ) {
        String clientIp = getClientIp(httpRequest);
        return ResponseEntity.ok(adminBankService.updateBankConfig(bankCode, request, adminUserId, clientIp));
    }

    @GetMapping("/integrations/health")
    public ResponseEntity<IntegrationHealthResponse> getIntegrationHealth() {
        return ResponseEntity.ok(adminBankService.getIntegrationHealth());
    }

    // ── 3. Transaction Monitoring & Reconciliation ─────────────

    @GetMapping("/transfers")
    public ResponseEntity<Page<AdminTransferSummaryResponse>> searchTransfers(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long senderId,
            @RequestParam(required = false) Long receiverId,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) String bankCode,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(adminTransferService.searchTransfers(
                userId, senderId, receiverId, status, bankCode, minAmount, maxAmount, reference, from, to, pageable));
    }

    @GetMapping("/transfers/{id}")
    public ResponseEntity<AdminTransferDetailResponse> getTransferDetail(@PathVariable Long id) {
        return ResponseEntity.ok(adminTransferService.getTransferDetail(id));
    }

    /**
     * Enforce security rule: admins cannot edit bank-confirmed transactions.
     */
    @PatchMapping("/transfers/{id}")
    public ResponseEntity<Void> editTransfer(@PathVariable Long id) {
        adminTransferService.validateNotBankConfirmed(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping({"/transfers/uncertain-queue", "/transfers/uncertain"})
    public ResponseEntity<Page<AdminTransferSummaryResponse>> getUncertainQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        return ResponseEntity.ok(adminTransferService.getUncertainQueue(pageable));
    }

    @PostMapping("/transfers/{id}/reconcile")
    public ResponseEntity<ReconcileResultResponse> triggerReconciliation(
            @PathVariable Long id,
            @AuthenticationPrincipal Long adminUserId,
            HttpServletRequest httpRequest
    ) {
        String clientIp = getClientIp(httpRequest);
        return ResponseEntity.ok(adminTransferService.triggerReconciliation(id, adminUserId, clientIp));
    }

    @PostMapping("/transfers/reconcile-all")
    public ResponseEntity<List<ReconcileResultResponse>> triggerReconcileAll(
            @AuthenticationPrincipal Long adminUserId,
            HttpServletRequest httpRequest
    ) {
        String clientIp = getClientIp(httpRequest);
        return ResponseEntity.ok(adminTransferService.triggerReconcileAll(adminUserId, clientIp));
    }

    // ── 4. Risk Rules & Fraud Alerts ───────────────────────────

    @GetMapping("/risk/rules")
    public ResponseEntity<RiskRulesResponse> getRiskRules() {
        return ResponseEntity.ok(riskManagementService.getRiskRules());
    }

    @PutMapping("/risk/rules")
    public ResponseEntity<RiskRulesResponse> updateRiskRules(
            @Valid @RequestBody UpdateRiskRulesRequest request,
            @AuthenticationPrincipal Long adminUserId,
            HttpServletRequest httpRequest
    ) {
        String clientIp = getClientIp(httpRequest);
        return ResponseEntity.ok(riskManagementService.updateRiskRules(request, adminUserId, clientIp));
    }

    @GetMapping({"/risk/fraud-alerts", "/fraud-alerts"})
    public ResponseEntity<Page<FraudAlertResponse>> getFraudAlerts(
            @RequestParam(required = false) FraudAlertStatus status,
            @RequestParam(required = false) FraudAlertSeverity severity,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(riskManagementService.getFraudAlerts(status, severity, userId, pageable));
    }

    @GetMapping("/risk/fraud-alerts/{id}")
    public ResponseEntity<FraudAlertResponse> getFraudAlert(@PathVariable Long id) {
        return ResponseEntity.ok(riskManagementService.getFraudAlert(id));
    }

    @PatchMapping("/risk/fraud-alerts/{id}/status")
    public ResponseEntity<FraudAlertResponse> updateFraudAlertStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFraudAlertStatusRequest request,
            @AuthenticationPrincipal Long adminUserId,
            HttpServletRequest httpRequest
    ) {
        String clientIp = getClientIp(httpRequest);
        return ResponseEntity.ok(riskManagementService.updateFraudAlertStatus(id, request, adminUserId, clientIp));
    }

    // ── 5. Audit Log Search ────────────────────────────────────

    @GetMapping("/audit-log")
    public ResponseEntity<Page<AdminAuditLogResponse>> searchAuditLogs(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return ResponseEntity.ok(adminAuditService.searchAuditLogs(
                actorId, eventType, entityType, entityId, from, to, pageable));
    }

    // ── helpers ────────────────────────────────────────────────

    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "UNKNOWN";
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        } else if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "UNKNOWN";
    }
}
