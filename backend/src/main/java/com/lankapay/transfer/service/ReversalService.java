package com.lankapay.transfer.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.card.domain.CardStore;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.identity.domain.User;
import com.lankapay.transfer.domain.FundingType;
import com.lankapay.transfer.domain.TransactionEvent;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.TransactionStore;
import com.lankapay.transfer.domain.Transfer;
import com.lankapay.virtualcard.provider.CardProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final TransactionStore transactionStore;
    private final AuditService auditService;
    private final CardStore cardStore;
    private final CardProvider cardProvider;
    private final CardDailyLimitService cardDailyLimitService;

    public ReversalService(TransactionStore transactionStore, AuditService auditService) {
        this(transactionStore, auditService, null, null, null);
    }

    @Autowired
    public ReversalService(TransactionStore transactionStore,
                           AuditService auditService,
                           @Autowired(required = false) CardStore cardStore,
                           @Autowired(required = false) CardProvider cardProvider,
                           @Autowired(required = false) CardDailyLimitService cardDailyLimitService) {
        this.transactionStore = transactionStore;
        this.auditService = auditService;
        this.cardStore = cardStore;
        this.cardProvider = cardProvider;
        this.cardDailyLimitService = cardDailyLimitService;
    }

    /**
     * Request reversal for a completed transfer.
     */
    @Transactional
    public Transfer requestReversal(Long transferId, Long requesterId, String reason, String clientIp) {
        Transfer transfer = loadTransfer(transferId);

        if (!transfer.getSender().getId().equals(requesterId)) {
            throw new AuthorizationException("FORBIDDEN",
                    "Only the sender can request a reversal for this transfer");
        }

        if (transfer.getStatus() != TransactionStatus.COMPLETED) {
            throw new ConflictException("INVALID_STATE_FOR_REVERSAL",
                    "Only COMPLETED transfers can be reversed. Current status: " + transfer.getStatus());
        }

        TransactionEvent event = transfer.requestReversal(reason);
        persistReversal(transfer, event);

        auditService.log(requesterId, "TRANSFER_REVERSAL_REQUESTED", "TRANSFER",
                transferId, "{\"reason\":\"" + reason + "\"}", clientIp);

        log.info("Reversal requested for transfer id={}, reason='{}'", transferId, reason);
        return transfer;
    }

    /**
     * Confirm reversal completed by the bank or card provider.
     */
    @Transactional
    public Transfer confirmReversal(Long transferId, String providerRef, String clientIp) {
        Transfer transfer = loadTransfer(transferId);

        if (transfer.getStatus() != TransactionStatus.REVERSAL_PENDING) {
            throw new ConflictException("INVALID_STATE_FOR_REVERSAL",
                    "Transfer must be in REVERSAL_PENDING to confirm reversal. Current status: " + transfer.getStatus());
        }

        if (transfer.getFundingType() == FundingType.CARD) {
            String opRef = transfer.getBankTxRef() != null ? transfer.getBankTxRef() : providerRef;
            if (cardProvider != null) {
                try {
                    cardProvider.voidAuth(opRef, transfer.getReference());
                } catch (Exception ex) {
                    log.warn("voidAuth call failed for card transfer id={}: {}", transferId, ex.getMessage());
                }
            }
            if (cardStore != null && cardDailyLimitService != null && transfer.getCardId() != null) {
                cardStore.findById(transfer.getCardId())
                        .ifPresent(card -> cardDailyLimitService.release(card, transfer.getAmount()));
            }

            TransactionEvent event = transfer.recordCardVoided(providerRef);
            persistReversal(transfer, event);

            auditService.log(null, "CARD_TRANSFER_REVERSED", "TRANSFER",
                    transferId, "{\"providerRef\":\"" + providerRef + "\"}", clientIp);

            log.info("Card reversal confirmed for transfer id={}, providerRef='{}'", transferId, providerRef);
            return transfer;
        }

        TransactionEvent event = transfer.reversed(providerRef);
        persistReversal(transfer, event);

        auditService.log(null, "TRANSFER_REVERSED", "TRANSFER",
                transferId, "{\"providerRef\":\"" + providerRef + "\"}", clientIp);

        log.info("Reversal confirmed for transfer id={}, providerRef='{}'", transferId, providerRef);
        return transfer;
    }

    private Transfer loadTransfer(Long transferId) {
        var r = transactionStore.findTransferById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("TRANSFER_NOT_FOUND",
                        "Transfer not found with id: " + transferId));
        User sender = new User(r.senderPhone(), r.senderFullName(), null, null);
        sender.setId(r.senderId());
        User receiver = new User(r.receiverPhone(), r.receiverFullName(), null, null);
        receiver.setId(r.receiverId());
        Transfer transfer = new Transfer(r.reference(), sender, receiver, r.amount(), r.currency(), r.initiatedVia());
        transfer.setId(r.id());
        transfer.setStatus(r.status());
        transfer.setBankTxRef(r.bankTxRef());
        transfer.setProviderReference(r.providerReference());
        transfer.setVersion(r.version());
        transfer.setSenderBankCode(r.senderBankCode());
        transfer.setReceiverBankCode(r.receiverBankCode());
        transfer.setFundingType(r.fundingType());
        transfer.setCardId(r.cardId());
        transfer.setCardLast4(r.cardLast4());
        transfer.setCardKind(r.cardKind());
        transfer.setFee(r.fee());
        transfer.setTotalAmount(r.totalAmount());
        return transfer;
    }

    private void persistReversal(Transfer transfer, TransactionEvent event) {
        if (transfer.getId() != null) {
            var updated = transactionStore.transitionTransferStatus(
                    transfer.getId(),
                    transfer.getStatus(),
                    event.getReason(),
                    event.getDetailJson(),
                    transfer.getBankTxRef(),
                    transfer.getProviderReference(),
                    transfer.getFailureReason(),
                    transfer.getVersion()
            );
            if (updated != null) {
                transfer.setVersion(updated.version());
            }
        }
    }
}
