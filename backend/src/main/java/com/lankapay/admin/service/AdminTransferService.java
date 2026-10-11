package com.lankapay.admin.service;

import com.lankapay.admin.repository.AdminTransferSpecification;
import com.lankapay.admin.web.dto.AdminTransactionEventResponse;
import com.lankapay.admin.web.dto.AdminTransferDetailResponse;
import com.lankapay.admin.web.dto.AdminTransferSummaryResponse;
import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.Transfer;
import com.lankapay.transfer.repository.TransactionEventRepository;
import com.lankapay.transfer.repository.TransferRepository;
import com.lankapay.transfer.service.ReconciliationService;
import com.lankapay.transfer.web.dto.ReconcileResultResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Service for administrative transfer monitoring, queue review, and reconciliation.
 *
 * Rules:
 * - Admins CANNOT edit bank-confirmed transactions (COMPLETED, REVERSED).
 * - Every manual reconciliation trigger is audited.
 */
@Service
public class AdminTransferService {

    private static final Logger log = LoggerFactory.getLogger(AdminTransferService.class);

    private final TransferRepository transferRepository;
    private final TransactionEventRepository transactionEventRepository;
    private final ReconciliationService reconciliationService;
    private final AuditService auditService;

    public AdminTransferService(TransferRepository transferRepository,
                                TransactionEventRepository transactionEventRepository,
                                ReconciliationService reconciliationService,
                                AuditService auditService) {
        this.transferRepository = transferRepository;
        this.transactionEventRepository = transactionEventRepository;
        this.reconciliationService = reconciliationService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<AdminTransferSummaryResponse> searchTransfers(Long userId,
                                                              Long senderId,
                                                              Long receiverId,
                                                              TransactionStatus status,
                                                              String bankCode,
                                                              BigDecimal minAmount,
                                                              BigDecimal maxAmount,
                                                              String reference,
                                                              Instant from,
                                                              Instant to,
                                                              Pageable pageable) {
        return transferRepository.findAll(AdminTransferSpecification.withFilters(
                userId, senderId, receiverId, status, bankCode, minAmount, maxAmount, reference, from, to), pageable)
                .map(AdminTransferSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminTransferDetailResponse getTransferDetail(Long transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("TRANSFER_NOT_FOUND", "Transfer not found with id: " + transferId));

        List<AdminTransactionEventResponse> events = transactionEventRepository
                .findByTransferIdOrderByOccurredAtAsc(transferId).stream()
                .map(AdminTransactionEventResponse::from)
                .toList();

        return AdminTransferDetailResponse.from(transfer, events);
    }

    @Transactional(readOnly = true)
    public Page<AdminTransferSummaryResponse> getUncertainQueue(Pageable pageable) {
        List<TransactionStatus> uncertainStatuses = List.of(
                TransactionStatus.REQUIRES_RECONCILIATION,
                TransactionStatus.PROCESSING,
                TransactionStatus.FAILED,
                TransactionStatus.REVERSAL_PENDING
        );
        return transferRepository.findAll((root, query, cb) -> root.get("status").in(uncertainStatuses), pageable)
                .map(AdminTransferSummaryResponse::from);
    }

    @Transactional
    public ReconcileResultResponse triggerReconciliation(Long transferId, Long adminUserId, String clientIp) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("TRANSFER_NOT_FOUND", "Transfer not found with id: " + transferId));

        log.info("[ADMIN] Admin id={} triggered manual reconciliation for transfer id={}, ref={}",
                adminUserId, transferId, transfer.getReference());

        auditService.log(adminUserId, "ADMIN_TRIGGER_RECONCILIATION", "TRANSFER", transferId,
                "{\"reference\":\"%s\",\"status\":\"%s\"}".formatted(transfer.getReference(), transfer.getStatus()), clientIp);

        return reconciliationService.reconcileTransfer(transferId);
    }

    @Transactional
    public List<ReconcileResultResponse> triggerReconcileAll(Long adminUserId, String clientIp) {
        log.info("[ADMIN] Admin id={} triggered bulk reconciliation of all pending/uncertain transfers", adminUserId);

        auditService.log(adminUserId, "ADMIN_TRIGGER_RECONCILIATION_ALL", "SYSTEM", null,
                "{\"action\":\"RECONCILE_ALL_PENDING\"}", clientIp);

        return reconciliationService.reconcileAllPending();
    }

    /**
     * Prohibits admin editing of bank-confirmed transactions per Section 11/13 security constraints.
     */
    public void validateNotBankConfirmed(Long transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("TRANSFER_NOT_FOUND", "Transfer not found with id: " + transferId));

        if (transfer.getStatus() == TransactionStatus.COMPLETED || transfer.getStatus() == TransactionStatus.REVERSED) {
            throw new ValidationException("CANNOT_EDIT_CONFIRMED_TRANSACTION",
                    "Bank-confirmed transactions cannot be modified by admins. Status: " + transfer.getStatus());
        }
    }
}
