package com.lankapay.transfer.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.adapter.AdapterResponse;
import com.lankapay.bank.adapter.BankAdapter;
import com.lankapay.bank.adapter.BankAdapterRegistry;
import com.lankapay.bank.adapter.TransferEligibilityRequest;
import com.lankapay.bank.adapter.TransferRequest;
import com.lankapay.bank.domain.BankAccount;
import com.lankapay.common.exception.UnprocessableEntityException;
import com.lankapay.transfer.domain.TransactionEvent;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.Transfer;
import com.lankapay.transfer.repository.TransactionEventRepository;
import com.lankapay.transfer.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes transfer operations against BankAdapter per section 9.
 *
 * Rules:
 * - Pre-flight eligibility check performed before calling initiateTransfer.
 * - Concurrency safety ensured via @Version optimistic locking on Transfer.
 * - Timeout moves transfer to REQUIRES_RECONCILIATION, NEVER to FAILED.
 * - Receipts and responses must NEVER say SUCCESS until BankAdapter confirms.
 * - All state transitions write an append-only TransactionEvent.
 */
@Service
public class TransferProcessor {

    private static final Logger log = LoggerFactory.getLogger(TransferProcessor.class);

    private final TransferRepository transferRepository;
    private final TransactionEventRepository transactionEventRepository;
    private final BankAdapterRegistry bankAdapterRegistry;
    private final DailyLimitService dailyLimitService;
    private final AuditService auditService;

    public TransferProcessor(TransferRepository transferRepository,
                             TransactionEventRepository transactionEventRepository,
                             BankAdapterRegistry bankAdapterRegistry,
                             DailyLimitService dailyLimitService,
                             AuditService auditService) {
        this.transferRepository = transferRepository;
        this.transactionEventRepository = transactionEventRepository;
        this.bankAdapterRegistry = bankAdapterRegistry;
        this.dailyLimitService = dailyLimitService;
        this.auditService = auditService;
    }

    /**
     * Process transfer through BankAdapter and record all state transitions.
     *
     * @param transfer        The newly created Transfer entity (in PENDING)
     * @param senderAccount   Sender's verified bank account
     * @param receiverAccount Receiver's verified bank account
     * @param quoteRef        Optional quote reference locking the rate/fee
     * @param clientIp        Client IP for audit logging
     * @return The updated Transfer entity reflecting the adapter's outcome
     */
    @Transactional
    public Transfer process(Transfer transfer,
                            BankAccount senderAccount,
                            BankAccount receiverAccount,
                            String quoteRef,
                            String clientIp) {

        log.info("Processing transfer ref={} amount={} {} (sender={}, receiver={})",
                transfer.getReference(), transfer.getAmount(), transfer.getCurrency(),
                transfer.getSender().getId(), transfer.getReceiver().getId());

        // 1. Initial PENDING audit event
        TransactionEvent pendingEvent = new TransactionEvent(
                transfer, null, TransactionStatus.PENDING, "INITIATED", null);
        transactionEventRepository.save(pendingEvent);

        // 2. Pre-flight checks with BankAdapter
        BankAdapter adapter = bankAdapterRegistry.get(senderAccount.getBankCode());

        TransferEligibilityRequest eligRequest = new TransferEligibilityRequest(
                senderAccount.getBankCode(),
                senderAccount.getMaskedNumber(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getReference()
        );

        AdapterResponse eligResp = adapter.checkTransferEligibility(eligRequest);
        if (!eligResp.success()) {
            log.warn("Transfer eligibility rejected by adapter for ref={}: code={}, message={}",
                    transfer.getReference(), eligResp.errorCode(), eligResp.errorMessage());
            TransactionEvent failEvent = transfer.fail(
                    eligResp.errorMessage() != null ? eligResp.errorMessage() : "Transfer eligibility check failed");
            transferRepository.save(transfer);
            transactionEventRepository.save(failEvent);

            auditService.log(transfer.getSender().getId(), "TRANSFER_ELIGIBILITY_FAILED",
                    "TRANSFER", transfer.getId(), "{\"reason\":\"" + eligResp.errorCode() + "\"}", clientIp);

            throw new UnprocessableEntityException(
                    eligResp.errorCode() != null ? eligResp.errorCode() : "BANK_DEBIT_FAILED",
                    eligResp.errorMessage() != null ? eligResp.errorMessage() : "Transfer eligibility check failed"
            );
        }

        // 3. Move to PROCESSING
        TransactionEvent procEvent = transfer.startProcessing("SENT_TO_BANK");
        transferRepository.save(transfer);
        transactionEventRepository.save(procEvent);

        // 4. Call BankAdapter initiateTransfer
        TransferRequest txRequest = new TransferRequest(
                senderAccount.getBankCode(),
                senderAccount.getMaskedNumber(),
                receiverAccount.getMaskedNumber(),
                transfer.getAmount(),
                transfer.getCurrency(),
                quoteRef,
                transfer.getReference()
        );

        AdapterResponse adapterResp;
        try {
            adapterResp = adapter.initiateTransfer(txRequest);
        } catch (Exception ex) {
            log.error("Exception while communicating with BankAdapter for transfer ref={}: {}",
                    transfer.getReference(), ex.getMessage(), ex);

            // Invariant: Timeout moves to REQUIRES_RECONCILIATION, NEVER to FAILED!
            TransactionEvent timeoutEvent = transfer.requiresReconciliation("TIMEOUT_EXCEPTION");
            transfer = transferRepository.save(transfer);
            transactionEventRepository.save(timeoutEvent);

            auditService.log(transfer.getSender().getId(), "TRANSFER_TIMEOUT",
                    "TRANSFER", transfer.getId(), "{\"error\":\"TIMEOUT_EXCEPTION\"}", clientIp);

            return transfer;
        }

        // 5. Evaluate BankAdapter response
        String status = adapterResp.transferStatus();
        log.info("BankAdapter response for ref={}: success={}, transferStatus={}, providerRef={}",
                transfer.getReference(), adapterResp.success(), status, adapterResp.providerReference());

        if (adapterResp.success() && "COMPLETED".equalsIgnoreCase(status)) {
            // Success confirmed by adapter
            TransactionEvent completedEvent = transfer.complete(
                    adapterResp.providerReference(), adapterResp.providerReference());
            transfer = transferRepository.save(transfer);
            transactionEventRepository.save(completedEvent);

            // Record daily limit spend only on confirmed success
            dailyLimitService.recordSpend(transfer.getSender(), transfer.getAmount());

            auditService.log(transfer.getSender().getId(), "TRANSFER_COMPLETED",
                    "TRANSFER", transfer.getId(), "{\"providerRef\":\"" + adapterResp.providerReference() + "\"}", clientIp);

        } else if ("TIMEOUT".equalsIgnoreCase(status) || "TIMEOUT".equalsIgnoreCase(adapterResp.errorCode())) {
            // Invariant: BankAdapter timeout MUST move to REQUIRES_RECONCILIATION, NEVER to FAILED!
            TransactionEvent reconEvent = transfer.requiresReconciliation(adapterResp.providerReference());
            transfer = transferRepository.save(transfer);
            transactionEventRepository.save(reconEvent);

            auditService.log(transfer.getSender().getId(), "TRANSFER_REQUIRES_RECONCILIATION",
                    "TRANSFER", transfer.getId(), "{\"reason\":\"TIMEOUT\"}", clientIp);

        } else if ("PENDING".equalsIgnoreCase(status)) {
            // Delayed confirmation — must be reconciled via status query
            TransactionEvent reconEvent = transfer.requiresReconciliation(adapterResp.providerReference());
            transfer = transferRepository.save(transfer);
            transactionEventRepository.save(reconEvent);

            auditService.log(transfer.getSender().getId(), "TRANSFER_PENDING_CONFIRMATION",
                    "TRANSFER", transfer.getId(), "{\"reason\":\"DELAYED_CONFIRMATION\"}", clientIp);

        } else {
            // Permanent failure confirmed by BankAdapter
            String reason = adapterResp.errorMessage() != null ? adapterResp.errorMessage() : "BANK_REJECTED";
            TransactionEvent failEvent = transfer.fail(reason);
            transfer = transferRepository.save(transfer);
            transactionEventRepository.save(failEvent);

            auditService.log(transfer.getSender().getId(), "TRANSFER_FAILED",
                    "TRANSFER", transfer.getId(), "{\"reason\":\"" + reason + "\"}", clientIp);
        }

        return transfer;
    }
}
