package com.lankapay.transfer.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ResourceNotFoundException;
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
 * Manages the reversal workflow per section 9.
 *
 * Transitions:
 *   COMPLETED ──→ REVERSAL_PENDING ──→ REVERSED
 */
@Service
public class ReversalService {

    private static final Logger log = LoggerFactory.getLogger(ReversalService.class);

    private final TransferRepository transferRepository;
    private final TransactionEventRepository transactionEventRepository;
    private final AuditService auditService;

    public ReversalService(TransferRepository transferRepository,
                           TransactionEventRepository transactionEventRepository,
                           AuditService auditService) {
        this.transferRepository = transferRepository;
        this.transactionEventRepository = transactionEventRepository;
        this.auditService = auditService;
    }

    /**
     * Request reversal for a completed transfer.
     */
    @Transactional
    public Transfer requestReversal(Long transferId, Long requesterId, String reason, String clientIp) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("TRANSFER_NOT_FOUND",
                        "Transfer not found with id: " + transferId));

        if (!transfer.getSender().getId().equals(requesterId)) {
            throw new AuthorizationException("FORBIDDEN",
                    "Only the sender can request a reversal for this transfer");
        }

        if (transfer.getStatus() != TransactionStatus.COMPLETED) {
            throw new ConflictException("INVALID_STATE_FOR_REVERSAL",
                    "Only COMPLETED transfers can be reversed. Current status: " + transfer.getStatus());
        }

        TransactionEvent event = transfer.requestReversal(reason);
        transfer = transferRepository.save(transfer);
        transactionEventRepository.save(event);

        auditService.log(requesterId, "TRANSFER_REVERSAL_REQUESTED", "TRANSFER",
                transferId, "{\"reason\":\"" + reason + "\"}", clientIp);

        log.info("Reversal requested for transfer id={}, reason='{}'", transferId, reason);
        return transfer;
    }

    /**
     * Confirm reversal completed by the bank.
     */
    @Transactional
    public Transfer confirmReversal(Long transferId, String providerRef, String clientIp) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("TRANSFER_NOT_FOUND",
                        "Transfer not found with id: " + transferId));

        if (transfer.getStatus() != TransactionStatus.REVERSAL_PENDING) {
            throw new ConflictException("INVALID_STATE_FOR_REVERSAL",
                    "Transfer must be in REVERSAL_PENDING to confirm reversal. Current status: " + transfer.getStatus());
        }

        TransactionEvent event = transfer.reversed(providerRef);
        transfer = transferRepository.save(transfer);
        transactionEventRepository.save(event);

        auditService.log(null, "TRANSFER_REVERSED", "TRANSFER",
                transferId, "{\"providerRef\":\"" + providerRef + "\"}", clientIp);

        log.info("Reversal confirmed for transfer id={}, providerRef='{}'", transferId, providerRef);
        return transfer;
    }
}
