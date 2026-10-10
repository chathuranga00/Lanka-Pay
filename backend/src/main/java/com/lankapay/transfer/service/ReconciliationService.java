package com.lankapay.transfer.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.adapter.AdapterResponse;
import com.lankapay.bank.adapter.BankAdapter;
import com.lankapay.bank.adapter.BankAdapterRegistry;
import com.lankapay.bank.adapter.TransferStatusRequest;
import com.lankapay.bank.domain.BankAccount;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.repository.BankAccountRepository;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.transfer.domain.TransactionEvent;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.Transfer;
import com.lankapay.transfer.repository.TransactionEventRepository;
import com.lankapay.transfer.repository.TransferRepository;
import com.lankapay.transfer.web.dto.ReconcileResultResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * ReconciliationService per section 9.
 *
 * Rules:
 * - Queries BankAdapter for pending/timeout transfers.
 * - Resolves to COMPLETED, FAILED, or REVERSED based on bank response.
 * - NO blind retries — always queries status.
 * - All status transitions emit TransactionEvent audit records.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final TransferRepository transferRepository;
    private final TransactionEventRepository transactionEventRepository;
    private final BankAccountRepository bankAccountRepository;
    private final BankAdapterRegistry bankAdapterRegistry;
    private final DailyLimitService dailyLimitService;
    private final AuditService auditService;

    public ReconciliationService(TransferRepository transferRepository,
                                 TransactionEventRepository transactionEventRepository,
                                 BankAccountRepository bankAccountRepository,
                                 BankAdapterRegistry bankAdapterRegistry,
                                 DailyLimitService dailyLimitService,
                                 AuditService auditService) {
        this.transferRepository = transferRepository;
        this.transactionEventRepository = transactionEventRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.bankAdapterRegistry = bankAdapterRegistry;
        this.dailyLimitService = dailyLimitService;
        this.auditService = auditService;
    }

    /**
     * Reconcile a single transfer by querying the BankAdapter.
     */
    @Transactional
    public ReconcileResultResponse reconcileTransfer(Long transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("TRANSFER_NOT_FOUND",
                        "Transfer not found with id: " + transferId));

        TransactionStatus prevStatus = transfer.getStatus();

        // Terminal states cannot be transitioned further
        if (transfer.getStatus().isTerminal()) {
            return new ReconcileResultResponse(
                    transfer.getId(),
                    transfer.getReference(),
                    prevStatus,
                    transfer.getStatus(),
                    false,
                    "Transfer is already in terminal state: " + prevStatus
            );
        }

        // Resolve bank institution code from sender's default account
        BankAccount senderAccount = bankAccountRepository
                .findByUserIdAndIsDefaultTrueAndStatus(transfer.getSender().getId(), BankAccountStatus.ACTIVE)
                .orElseGet(() -> bankAccountRepository
                        .findByUserIdAndStatus(transfer.getSender().getId(), BankAccountStatus.ACTIVE)
                        .stream().findFirst()
                        .orElseThrow(() -> new IllegalStateException("No active bank account for sender id=" + transfer.getSender().getId())));

        BankAdapter adapter = bankAdapterRegistry.get(senderAccount.getBankCode());

        TransferStatusRequest queryRequest = new TransferStatusRequest(
                senderAccount.getBankCode(),
                transfer.getProviderReference(),
                transfer.getReference()
        );

        log.info("Reconciling transfer id={}, ref={} with adapter {}",
                transfer.getId(), transfer.getReference(), senderAccount.getBankCode());

        AdapterResponse resp = adapter.queryTransferStatus(queryRequest);

        log.info("Reconciliation response for transfer id={}: status={}, providerRef={}",
                transfer.getId(), resp.transferStatus(), resp.providerReference());

        String statusStr = resp.transferStatus();

        if (resp.success() && "COMPLETED".equalsIgnoreCase(statusStr)) {
            // Reconciled to COMPLETED
            TransactionEvent event = transfer.complete(resp.providerReference(), resp.providerReference());
            transferRepository.save(transfer);
            transactionEventRepository.save(event);

            dailyLimitService.recordSpend(transfer.getSender(), transfer.getAmount());

            auditService.log(null, "TRANSFER_RECONCILED_COMPLETED", "TRANSFER",
                    transfer.getId(), "{\"providerRef\":\"" + resp.providerReference() + "\"}", "SYSTEM");

            return new ReconcileResultResponse(
                    transfer.getId(),
                    transfer.getReference(),
                    prevStatus,
                    TransactionStatus.COMPLETED,
                    true,
                    "Reconciled successfully to COMPLETED"
            );

        } else if ("REVERSED".equalsIgnoreCase(statusStr)) {
            // Reconciled to REVERSED
            if (transfer.getStatus() == TransactionStatus.COMPLETED) {
                TransactionEvent pendingRev = transfer.requestReversal("REVERSAL_REPORTED_BY_BANK");
                transactionEventRepository.save(pendingRev);
            }
            TransactionEvent revEvent = transfer.reversed(resp.providerReference());
            transferRepository.save(transfer);
            transactionEventRepository.save(revEvent);

            auditService.log(null, "TRANSFER_RECONCILED_REVERSED", "TRANSFER",
                    transfer.getId(), "{\"providerRef\":\"" + resp.providerReference() + "\"}", "SYSTEM");

            return new ReconcileResultResponse(
                    transfer.getId(),
                    transfer.getReference(),
                    prevStatus,
                    TransactionStatus.REVERSED,
                    true,
                    "Reconciled successfully to REVERSED"
            );

        } else if ("FAILED".equalsIgnoreCase(statusStr) || (!resp.success() && !"TIMEOUT".equalsIgnoreCase(statusStr) && !"PENDING".equalsIgnoreCase(statusStr))) {
            // Reconciled to FAILED
            String reason = resp.errorMessage() != null ? resp.errorMessage() : "FAILED_AT_BANK";
            TransactionEvent failEvent = transfer.fail(reason);
            transferRepository.save(transfer);
            transactionEventRepository.save(failEvent);

            auditService.log(null, "TRANSFER_RECONCILED_FAILED", "TRANSFER",
                    transfer.getId(), "{\"reason\":\"" + reason + "\"}", "SYSTEM");

            return new ReconcileResultResponse(
                    transfer.getId(),
                    transfer.getReference(),
                    prevStatus,
                    TransactionStatus.FAILED,
                    true,
                    "Reconciled to FAILED: " + reason
            );

        } else {
            // Still pending or timed out at bank — keep in REQUIRES_RECONCILIATION
            return new ReconcileResultResponse(
                    transfer.getId(),
                    transfer.getReference(),
                    prevStatus,
                    transfer.getStatus(),
                    false,
                    "Bank reported status " + statusStr + "; still requires reconciliation"
            );
        }
    }

    /**
     * Reconcile all transfers currently requiring reconciliation.
     */
    @Transactional
    public List<ReconcileResultResponse> reconcileAllPending() {
        List<Transfer> needRecon = transferRepository.findByStatusIn(List.of(
                TransactionStatus.REQUIRES_RECONCILIATION,
                TransactionStatus.PROCESSING
        ));

        List<ReconcileResultResponse> results = new ArrayList<>();
        for (Transfer t : needRecon) {
            try {
                results.add(reconcileTransfer(t.getId()));
            } catch (Exception e) {
                log.error("Failed to reconcile transfer id={}: {}", t.getId(), e.getMessage(), e);
                results.add(new ReconcileResultResponse(
                        t.getId(),
                        t.getReference(),
                        t.getStatus(),
                        t.getStatus(),
                        false,
                        "Reconciliation error: " + e.getMessage()
                ));
            }
        }
        return results;
    }
}
