package com.lankapay.admin.service;

import com.lankapay.admin.web.dto.AdminTransactionEventResponse;
import com.lankapay.admin.web.dto.AdminTransferDetailResponse;
import com.lankapay.admin.web.dto.AdminTransferSummaryResponse;
import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.transfer.domain.TransactionRecord;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.TransactionStore;
import com.lankapay.transfer.service.ReconciliationService;
import com.lankapay.transfer.web.dto.ReconcileResultResponse;
import com.lankapay.transfer.web.dto.TransferDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

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

    private final TransactionStore transactionStore;
    private final ReconciliationService reconciliationService;
    private final AuditService auditService;

    public AdminTransferService(TransactionStore transactionStore,
                                ReconciliationService reconciliationService,
                                AuditService auditService) {
        this.transactionStore = transactionStore;
        this.reconciliationService = reconciliationService;
        this.auditService = auditService;
    }

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
        if (reference != null && !reference.isBlank()) {
            return transactionStore.findTransferByReference(reference.trim())
                    .map(tx -> new PageImpl<>(List.of(AdminTransferSummaryResponse.from(tx)), pageable, 1))
                    .orElseGet(() -> new PageImpl<>(List.of(), pageable, 0));
        }

        Long targetUserId = userId != null ? userId : (senderId != null ? senderId : receiverId);
        TransferDirection direction = senderId != null ? TransferDirection.SENT
                : (receiverId != null ? TransferDirection.RECEIVED : TransferDirection.ALL);

        return transactionStore.searchTransfers(
                targetUserId,
                direction,
                status,
                from,
                to,
                bankCode,
                minAmount,
                maxAmount,
                pageable
        ).map(AdminTransferSummaryResponse::from);
    }

    public AdminTransferDetailResponse getTransferDetail(Long transferId) {
        TransactionRecord transfer = transactionStore.findTransferById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("TRANSFER_NOT_FOUND", "Transfer not found with id: " + transferId));

        List<AdminTransactionEventResponse> events = transactionStore.findEventsByTransferId(transferId).stream()
                .map(AdminTransactionEventResponse::from)
                .toList();

        return AdminTransferDetailResponse.from(transfer, events);
    }

    public Page<AdminTransferSummaryResponse> getUncertainQueue(Pageable pageable) {
        List<TransactionStatus> uncertainStatuses = List.of(
                TransactionStatus.REQUIRES_RECONCILIATION,
                TransactionStatus.PROCESSING,
                TransactionStatus.FAILED,
                TransactionStatus.REVERSAL_PENDING
        );
        List<TransactionRecord> records = transactionStore.findTransfersByStatusIn(uncertainStatuses);
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();
        if (offset >= records.size()) {
            return new PageImpl<>(List.of(), pageable, records.size());
        }
        int end = Math.min(offset + limit, records.size());
        List<AdminTransferSummaryResponse> paged = records.subList(offset, end).stream()
                .map(AdminTransferSummaryResponse::from)
                .toList();
        return new PageImpl<>(paged, pageable, records.size());
    }

    public ReconcileResultResponse triggerReconciliation(Long transferId, Long adminUserId, String clientIp) {
        TransactionRecord transfer = transactionStore.findTransferById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("TRANSFER_NOT_FOUND", "Transfer not found with id: " + transferId));

        log.info("[ADMIN] Admin id={} triggered manual reconciliation for transfer id={}, ref={}",
                adminUserId, transferId, transfer.reference());

        auditService.log(adminUserId, "ADMIN_TRIGGER_RECONCILIATION", "TRANSFER", transferId,
                "{\"reference\":\"%s\",\"status\":\"%s\"}".formatted(transfer.reference(), transfer.status()), clientIp);

        return reconciliationService.reconcileTransfer(transferId);
    }

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
        TransactionRecord transfer = transactionStore.findTransferById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("TRANSFER_NOT_FOUND", "Transfer not found with id: " + transferId));

        if (transfer.status() == TransactionStatus.COMPLETED || transfer.status() == TransactionStatus.REVERSED) {
            throw new ValidationException("CANNOT_EDIT_CONFIRMED_TRANSACTION",
                    "Bank-confirmed transactions cannot be modified by admins. Status: " + transfer.status());
        }
    }
}
