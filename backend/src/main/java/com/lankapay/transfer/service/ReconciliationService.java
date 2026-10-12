package com.lankapay.transfer.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.adapter.AdapterResponse;
import com.lankapay.bank.adapter.BankAdapter;
import com.lankapay.bank.adapter.BankAdapterRegistry;
import com.lankapay.bank.adapter.TransferStatusRequest;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.card.domain.Card;
import com.lankapay.card.domain.CardStore;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.identity.domain.User;
import com.lankapay.transfer.domain.FundingType;
import com.lankapay.transfer.domain.TransactionEvent;
import com.lankapay.transfer.domain.TransactionRecord;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.TransactionStore;
import com.lankapay.transfer.domain.Transfer;
import com.lankapay.transfer.web.dto.ReconcileResultResponse;
import com.lankapay.virtualcard.provider.CardAuthResponse;
import com.lankapay.virtualcard.provider.CardAuthStatus;
import com.lankapay.virtualcard.provider.CardProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final TransactionStore transactionStore;
    private final LinkedAccountStore linkedAccountStore;
    private final BankAdapterRegistry bankAdapterRegistry;
    private final DailyLimitService dailyLimitService;
    private final AuditService auditService;
    private final CardStore cardStore;
    private final CardProvider cardProvider;
    private final CardDailyLimitService cardDailyLimitService;

    public ReconciliationService(TransactionStore transactionStore,
                                 LinkedAccountStore linkedAccountStore,
                                 BankAdapterRegistry bankAdapterRegistry,
                                 DailyLimitService dailyLimitService,
                                 AuditService auditService) {
        this(transactionStore, linkedAccountStore, bankAdapterRegistry, dailyLimitService, auditService, null, null, null);
    }

    @Autowired
    public ReconciliationService(TransactionStore transactionStore,
                                 @Autowired(required = false) LinkedAccountStore linkedAccountStore,
                                 BankAdapterRegistry bankAdapterRegistry,
                                 DailyLimitService dailyLimitService,
                                 AuditService auditService,
                                 @Autowired(required = false) CardStore cardStore,
                                 @Autowired(required = false) CardProvider cardProvider,
                                 @Autowired(required = false) CardDailyLimitService cardDailyLimitService) {
        this.transactionStore = transactionStore;
        this.linkedAccountStore = linkedAccountStore;
        this.bankAdapterRegistry = bankAdapterRegistry;
        this.dailyLimitService = dailyLimitService;
        this.auditService = auditService;
        this.cardStore = cardStore;
        this.cardProvider = cardProvider;
        this.cardDailyLimitService = cardDailyLimitService;
    }

    /**
     * Reconcile a single transfer by querying the BankAdapter.
     * System or admin entry point without user-level scoping.
     */
    @Transactional
    public ReconcileResultResponse reconcileTransfer(Long transferId) {
        return reconcileTransfer(transferId, null);
    }

    /**
     * Reconcile a single transfer by querying the BankAdapter.
     * If authenticatedUserId is non-null, validates that user is party to transfer (IDOR protection).
     */
    @Transactional
    public ReconcileResultResponse reconcileTransfer(Long transferId, Long authenticatedUserId) {
        TransactionRecord r = transactionStore.findTransferById(transferId)
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

        if (authenticatedUserId != null) {
            boolean isSender = transfer.getSender() != null && transfer.getSender().getId().equals(authenticatedUserId);
            boolean isReceiver = transfer.getReceiver() != null && transfer.getReceiver().getId().equals(authenticatedUserId);
            if (!isSender && !isReceiver) {
                throw new AuthorizationException("FORBIDDEN",
                        "You do not have permission to reconcile transfer id: " + transferId);
            }
        }

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

        // ── Card Reconciliation ────────────────────────────────────
        if (transfer.getFundingType() == FundingType.CARD) {
            if (cardProvider == null || cardStore == null) {
                throw new IllegalStateException("Card services unavailable for reconciliation");
            }
            Card card = cardStore.findById(transfer.getCardId())
                    .orElseThrow(() -> new ResourceNotFoundException("CARD_NOT_FOUND", "Card not found: " + transfer.getCardId()));

            log.info("Reconciling card transfer id={}, ref={} with cardProvider",
                    transfer.getId(), transfer.getReference());

            CardAuthResponse authResp = cardProvider.queryPayment(transfer.getReference(), card.cardToken());

            log.info("Card reconciliation response for transfer id={}: approved={}, status={}, authCode={}",
                    transfer.getId(), authResp.approved(), authResp.status(), authResp.authCode());

            if (authResp.approved() && authResp.status() == CardAuthStatus.APPROVED) {
                TransactionEvent event = transfer.recordCardCaptured(authResp.authCode() != null ? authResp.authCode() : transfer.getReference());
                persistReconciliation(transfer, event);
                if (cardDailyLimitService != null) {
                    cardDailyLimitService.commit(card, transfer.getAmount());
                }
                auditService.log(null, "CARD_TRANSFER_RECONCILED_COMPLETED", "TRANSFER",
                        transfer.getId(), "{\"authCode\":\"" + authResp.authCode() + "\"}", "SYSTEM");

                return new ReconcileResultResponse(
                        transfer.getId(),
                        transfer.getReference(),
                        prevStatus,
                        TransactionStatus.COMPLETED,
                        true,
                        "Reconciled successfully to COMPLETED"
                );
            } else if (authResp.status() == CardAuthStatus.REVERSED || "Authorization reversed by issuer".equalsIgnoreCase(authResp.declineReason())) {
                if (transfer.getStatus() == TransactionStatus.COMPLETED) {
                    TransactionEvent pendingRev = transfer.requestReversal("REVERSAL_REPORTED_BY_PROVIDER");
                    persistReconciliation(transfer, pendingRev);
                }
                TransactionEvent revEvent = transfer.reversed(authResp.authCode());
                persistReconciliation(transfer, revEvent);
                if (cardDailyLimitService != null) {
                    cardDailyLimitService.release(card, transfer.getAmount());
                }
                auditService.log(null, "CARD_TRANSFER_RECONCILED_REVERSED", "TRANSFER",
                        transfer.getId(), "{\"reason\":\"" + authResp.declineReason() + "\"}", "SYSTEM");

                return new ReconcileResultResponse(
                        transfer.getId(),
                        transfer.getReference(),
                        prevStatus,
                        TransactionStatus.REVERSED,
                        true,
                        "Reconciled successfully to REVERSED"
                );
            } else if (authResp.status() == CardAuthStatus.DECLINED || (!authResp.approved() && authResp.status() != CardAuthStatus.TIMEOUT && authResp.status() != CardAuthStatus.PENDING_CHALLENGE)) {
                String reason = authResp.declineReason() != null ? authResp.declineReason() : "CARD_DECLINED";
                TransactionEvent failEvent = transfer.recordCardDeclined(reason);
                persistReconciliation(transfer, failEvent);
                if (cardDailyLimitService != null) {
                    cardDailyLimitService.release(card, transfer.getAmount());
                }
                auditService.log(null, "CARD_TRANSFER_RECONCILED_FAILED", "TRANSFER",
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
                return new ReconcileResultResponse(
                        transfer.getId(),
                        transfer.getReference(),
                        prevStatus,
                        transfer.getStatus(),
                        false,
                        "Provider reported status " + authResp.status() + "; still requires reconciliation"
                );
            }
        }

        // Resolve bank institution code from sender's default account or transfer record
        String bankCode = transfer.getSenderBankCode();
        if ((bankCode == null || bankCode.isBlank()) && linkedAccountStore != null && transfer.getSender() != null) {
            Long senderId = transfer.getSender().getId();
            var senderAccount = linkedAccountStore.findActiveDefaultAccount(senderId)
                    .orElseGet(() -> linkedAccountStore.findAccountsByUserIdAndStatus(senderId, BankAccountStatus.ACTIVE)
                            .stream().findFirst().orElse(null));
            if (senderAccount != null) {
                bankCode = senderAccount.bankCode();
            }
        }
        if (bankCode == null || bankCode.isBlank()) {
            bankCode = "SIMULATED_BANK_A";
        }

        BankAdapter adapter = bankAdapterRegistry.get(bankCode);

        TransferStatusRequest queryRequest = new TransferStatusRequest(
                bankCode,
                transfer.getProviderReference(),
                transfer.getReference()
        );

        log.info("Reconciling transfer id={}, ref={} with adapter {}",
                transfer.getId(), transfer.getReference(), bankCode);

        AdapterResponse resp = adapter.queryTransferStatus(queryRequest);

        log.info("Reconciliation response for transfer id={}: status={}, providerRef={}",
                transfer.getId(), resp.transferStatus(), resp.providerReference());

        String statusStr = resp.transferStatus();

        if (resp.success() && "COMPLETED".equalsIgnoreCase(statusStr)) {
            // Reconciled to COMPLETED
            TransactionEvent event = transfer.complete(resp.providerReference(), resp.providerReference());
            persistReconciliation(transfer, event);

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
                persistReconciliation(transfer, pendingRev);
            }
            TransactionEvent revEvent = transfer.reversed(resp.providerReference());
            persistReconciliation(transfer, revEvent);

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
            persistReconciliation(transfer, failEvent);

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

    private void persistReconciliation(Transfer transfer, TransactionEvent event) {
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

    /**
     * Reconcile all transfers currently requiring reconciliation.
     */
    @Transactional
    public List<ReconcileResultResponse> reconcileAllPending() {
        List<TransactionRecord> needRecon = transactionStore.findTransfersByStatusIn(List.of(
                TransactionStatus.REQUIRES_RECONCILIATION,
                TransactionStatus.PROCESSING
        ));

        List<ReconcileResultResponse> results = new ArrayList<>();
        for (TransactionRecord t : needRecon) {
            try {
                results.add(reconcileTransfer(t.id()));
            } catch (Exception e) {
                log.error("Failed to reconcile transfer id={}: {}", t.id(), e.getMessage(), e);
                results.add(new ReconcileResultResponse(
                        t.id(),
                        t.reference(),
                        t.status(),
                        t.status(),
                        false,
                        "Reconciliation error: " + e.getMessage()
                ));
            }
        }
        return results;
    }
}
