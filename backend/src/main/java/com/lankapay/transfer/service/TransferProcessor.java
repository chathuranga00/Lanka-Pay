package com.lankapay.transfer.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.adapter.AdapterResponse;
import com.lankapay.bank.adapter.BankAdapter;
import com.lankapay.bank.adapter.BankAdapterRegistry;
import com.lankapay.bank.adapter.TransferEligibilityRequest;
import com.lankapay.bank.adapter.TransferRequest;
import com.lankapay.bank.domain.BankAccount;
import com.lankapay.card.domain.Card;
import com.lankapay.common.exception.UnprocessableEntityException;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.transfer.domain.TransactionEvent;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.TransactionStore;
import com.lankapay.transfer.domain.Transfer;
import com.lankapay.virtualcard.provider.CardAuthRequest;
import com.lankapay.virtualcard.provider.CardAuthResponse;
import com.lankapay.virtualcard.provider.CardAuthStatus;
import com.lankapay.virtualcard.provider.CardProvider;
import com.lankapay.virtualcard.provider.CardScenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes transfer operations against BankAdapter or CardProvider per section 9.
 *
 * Rules:
 * - Pre-flight eligibility check performed before calling initiateTransfer.
 * - Concurrency safety ensured via optimistic locking on Transfer.
 * - Timeout moves transfer to REQUIRES_RECONCILIATION, NEVER to FAILED.
 * - Receipts and responses must NEVER say SUCCESS until BankAdapter/CardProvider confirms.
 * - All state transitions write an append-only TransactionEvent in Firestore.
 */
@Service
public class TransferProcessor {

    private static final Logger log = LoggerFactory.getLogger(TransferProcessor.class);

    private final TransactionStore transactionStore;
    private final BankAdapterRegistry bankAdapterRegistry;
    private final DailyLimitService dailyLimitService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final CardProvider cardProvider;
    private final CardDailyLimitService cardDailyLimitService;
    private final CardTransferFraudService cardFraudService;

    public TransferProcessor(TransactionStore transactionStore,
                             BankAdapterRegistry bankAdapterRegistry,
                             DailyLimitService dailyLimitService,
                             AuditService auditService,
                             NotificationService notificationService) {
        this(transactionStore, bankAdapterRegistry, dailyLimitService, auditService, notificationService, null, null, null);
    }

    @Autowired
    public TransferProcessor(TransactionStore transactionStore,
                             BankAdapterRegistry bankAdapterRegistry,
                             DailyLimitService dailyLimitService,
                             AuditService auditService,
                             @Autowired(required = false) NotificationService notificationService,
                             @Autowired(required = false) CardProvider cardProvider,
                             @Autowired(required = false) CardDailyLimitService cardDailyLimitService,
                             @Autowired(required = false) CardTransferFraudService cardFraudService) {
        this.transactionStore = transactionStore;
        this.bankAdapterRegistry = bankAdapterRegistry;
        this.dailyLimitService = dailyLimitService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.cardProvider = cardProvider;
        this.cardDailyLimitService = cardDailyLimitService;
        this.cardFraudService = cardFraudService;
    }

    /**
     * Process transfer through BankAdapter and record all state transitions.
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

        if (senderAccount != null) {
            transfer.setSenderBankCode(senderAccount.getBankCode());
        }
        if (receiverAccount != null) {
            transfer.setReceiverBankCode(receiverAccount.getBankCode());
        }

        // 1. Pre-flight checks with BankAdapter
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
            persistTransition(transfer, failEvent);

            auditService.log(transfer.getSender().getId(), "TRANSFER_ELIGIBILITY_FAILED",
                    "TRANSFER", transfer.getId(), "{\"reason\":\"" + eligResp.errorCode() + "\"}", clientIp);

            throw new UnprocessableEntityException(
                    eligResp.errorCode() != null ? eligResp.errorCode() : "BANK_DEBIT_FAILED",
                    eligResp.errorMessage() != null ? eligResp.errorMessage() : "Transfer eligibility check failed"
            );
        }

        // 2. Move to PROCESSING
        TransactionEvent procEvent = transfer.startProcessing("SENT_TO_BANK");
        persistTransition(transfer, procEvent);

        // 3. Call BankAdapter initiateTransfer
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
            persistTransition(transfer, timeoutEvent);

            auditService.log(transfer.getSender().getId(), "TRANSFER_TIMEOUT",
                    "TRANSFER", transfer.getId(), "{\"error\":\"TIMEOUT_EXCEPTION\"}", clientIp);

            return transfer;
        }

        // 4. Evaluate BankAdapter response
        String status = adapterResp.transferStatus();
        log.info("BankAdapter response for ref={}: success={}, transferStatus={}, providerRef={}",
                transfer.getReference(), adapterResp.success(), status, adapterResp.providerReference());

        if (adapterResp.success() && "COMPLETED".equalsIgnoreCase(status)) {
            // Success confirmed by adapter
            TransactionEvent completedEvent = transfer.complete(
                    adapterResp.providerReference(), adapterResp.providerReference());
            persistTransition(transfer, completedEvent);

            // Record daily limit spend only on confirmed success
            dailyLimitService.recordSpend(transfer.getSender(), transfer.getAmount());

            auditService.log(transfer.getSender().getId(), "TRANSFER_COMPLETED",
                    "TRANSFER", transfer.getId(), "{\"providerRef\":\"" + adapterResp.providerReference() + "\"}", clientIp);

            // Safe notification delivery: failure must never break payment
            if (notificationService != null) {
                try {
                    notificationService.notifyPaymentSent(transfer);
                    notificationService.notifyPaymentReceived(transfer);
                } catch (Exception ex) {
                    log.error("Notification delivery failure for transfer ref={}: {}",
                            transfer.getReference(), ex.getMessage());
                }
            }

        } else if ("TIMEOUT".equalsIgnoreCase(status) || "TIMEOUT".equalsIgnoreCase(adapterResp.errorCode())) {
            // Invariant: BankAdapter timeout MUST move to REQUIRES_RECONCILIATION, NEVER to FAILED!
            TransactionEvent reconEvent = transfer.requiresReconciliation(adapterResp.providerReference());
            persistTransition(transfer, reconEvent);

            auditService.log(transfer.getSender().getId(), "TRANSFER_REQUIRES_RECONCILIATION",
                    "TRANSFER", transfer.getId(), "{\"reason\":\"TIMEOUT\"}", clientIp);

        } else if ("PENDING".equalsIgnoreCase(status)) {
            // Delayed confirmation — must be reconciled via status query
            TransactionEvent reconEvent = transfer.requiresReconciliation(adapterResp.providerReference());
            persistTransition(transfer, reconEvent);

            auditService.log(transfer.getSender().getId(), "TRANSFER_PENDING_CONFIRMATION",
                    "TRANSFER", transfer.getId(), "{\"reason\":\"DELAYED_CONFIRMATION\"}", clientIp);

        } else {
            // Permanent failure confirmed by BankAdapter
            String reason = adapterResp.errorMessage() != null ? adapterResp.errorMessage() : "BANK_REJECTED";
            TransactionEvent failEvent = transfer.fail(reason);
            persistTransition(transfer, failEvent);

            auditService.log(transfer.getSender().getId(), "TRANSFER_FAILED",
                    "TRANSFER", transfer.getId(), "{\"reason\":\"" + reason + "\"}", clientIp);

            if (notificationService != null) {
                try {
                    notificationService.notifyPaymentFailed(transfer, reason);
                } catch (Exception ex) {
                    log.error("Notification delivery failure for failed transfer ref={}: {}",
                            transfer.getReference(), ex.getMessage());
                }
            }
        }

        return transfer;
    }

    /**
     * Process transfer funded by card via CardProvider.
     */
    @Transactional
    public Transfer processCardPayment(Transfer transfer,
                                       Card card,
                                       BankAccount receiverAccount,
                                       String clientIp) {

        log.info("Processing card transfer ref={} cardId={} last4={} amount={} {} (sender={}, receiver={})",
                transfer.getReference(), card.id(), card.last4(), transfer.getAmount(), transfer.getCurrency(),
                transfer.getSender().getId(), transfer.getReceiver().getId());

        if (receiverAccount != null) {
            transfer.setReceiverBankCode(receiverAccount.getBankCode());
        }

        CardAuthRequest authReq = new CardAuthRequest(
                card.cardToken(),
                transfer.getSender().getId(),
                transfer.getAmount(),
                transfer.getCurrency() != null ? transfer.getCurrency() : "LKR",
                transfer.getReference()
        );

        CardAuthResponse authResp;
        try {
            authResp = cardProvider.authorize(authReq);
        } catch (Exception ex) {
            log.error("Exception communicating with CardProvider for transfer ref={}: {}",
                    transfer.getReference(), ex.getMessage(), ex);

            TransactionEvent timeoutEvent = transfer.requiresReconciliation("TIMEOUT_EXCEPTION");
            persistTransition(transfer, timeoutEvent);

            auditService.log(transfer.getSender().getId(), "CARD_TRANSFER_TIMEOUT",
                    "TRANSFER", transfer.getId(), "{\"error\":\"TIMEOUT_EXCEPTION\"}", clientIp);

            return transfer;
        }

        log.info("CardProvider authorize response for ref={}: status={}, authCode={}, scenario={}",
                transfer.getReference(), authResp.status(), authResp.authCode(), authResp.scenario());

        if (authResp.status() == CardAuthStatus.PENDING_CHALLENGE) {
            if (authResp.scenario() == CardScenario.DELAYED_CONFIRMATION) {
                // Delayed confirmation moves to REQUIRES_RECONCILIATION
                TransactionEvent reconEvent = transfer.requiresReconciliation(authResp.authCode());
                persistTransition(transfer, reconEvent);

                auditService.log(transfer.getSender().getId(), "CARD_TRANSFER_DELAYED_CONFIRMATION",
                        "TRANSFER", transfer.getId(), "{\"reason\":\"DELAYED_CONFIRMATION\"}", clientIp);
                return transfer;
            }

            // OTP challenge required scenario
            String challengeRef = cardProvider.startOwnershipChallenge(card.cardToken(), transfer.getSender().getId(), clientIp);
            transfer.setChallengeRef(challengeRef);
            TransactionEvent chalEvent = transfer.requireChallenge(challengeRef);
            persistTransition(transfer, chalEvent);

            if (transactionStore != null) {
                transactionStore.updateTransferChallenge(transfer.getId(), challengeRef, 0);
            }

            auditService.log(transfer.getSender().getId(), "CARD_TRANSFER_CHALLENGE_REQUIRED",
                    "TRANSFER", transfer.getId(), "{\"challengeRef\":\"" + challengeRef + "\"}", clientIp);

            return transfer;
        }

        if (authResp.status() == CardAuthStatus.TIMEOUT) {
            TransactionEvent timeoutEvent = transfer.requiresReconciliation(authResp.authCode());
            persistTransition(transfer, timeoutEvent);

            auditService.log(transfer.getSender().getId(), "CARD_TRANSFER_TIMEOUT",
                    "TRANSFER", transfer.getId(), "{\"reason\":\"TIMEOUT\"}", clientIp);
            return transfer;
        }

        if (authResp.status() == CardAuthStatus.APPROVED) {
            TransactionEvent authEvent = transfer.recordCardAuthorized(authResp.authCode());
            persistTransition(transfer, authEvent);

            // Capture
            CardAuthResponse capResp;
            try {
                capResp = cardProvider.capture(authResp.authCode(), transfer.getReference());
            } catch (Exception ex) {
                log.error("Exception during card capture for transfer ref={}: {}", transfer.getReference(), ex.getMessage(), ex);
                TransactionEvent reconEvent = transfer.requiresReconciliation(authResp.authCode());
                persistTransition(transfer, reconEvent);
                return transfer;
            }

            if (capResp != null && capResp.approved()) {
                TransactionEvent capEvent = transfer.recordCardCaptured(authResp.authCode(), authResp.authCode());
                persistTransition(transfer, capEvent);

                if (cardDailyLimitService != null) {
                    cardDailyLimitService.commit(card, transfer.getAmount());
                }
                if (cardFraudService != null) {
                    cardFraudService.recordSuccess(card.id());
                }

                auditService.log(transfer.getSender().getId(), "CARD_TRANSFER_COMPLETED",
                        "TRANSFER", transfer.getId(), "{\"authCode\":\"" + authResp.authCode() + "\"}", clientIp);

                if (notificationService != null) {
                    try {
                        notificationService.notifyPaymentSent(transfer);
                        notificationService.notifyPaymentReceived(transfer);
                    } catch (Exception ex) {
                        log.error("Notification delivery failure for card transfer ref={}: {}",
                                transfer.getReference(), ex.getMessage());
                    }
                }
            } else {
                TransactionEvent reconEvent = transfer.requiresReconciliation(authResp.authCode());
                persistTransition(transfer, reconEvent);
            }

            return transfer;
        }

        // Decline branch
        if (cardDailyLimitService != null) {
            cardDailyLimitService.release(card, transfer.getAmount());
        }

        String safeCode = mapDeclineCode(authResp);
        String safeMessage = mapDeclineMessage(safeCode);

        if (cardFraudService != null) {
            cardFraudService.recordDecline(card.id(), safeCode);
        }

        TransactionEvent failEvent = transfer.recordCardDeclined(safeCode);
        persistTransition(transfer, failEvent);

        auditService.log(transfer.getSender().getId(), "CARD_TRANSFER_DECLINED",
                "TRANSFER", transfer.getId(), "{\"reason\":\"" + safeCode + "\"}", clientIp);

        if (notificationService != null) {
            try {
                notificationService.notifyPaymentFailed(transfer, safeCode);
            } catch (Exception ex) {
                log.error("Notification delivery failure for failed card transfer ref={}: {}",
                        transfer.getReference(), ex.getMessage());
            }
        }

        throw new UnprocessableEntityException(safeCode, safeMessage);
    }

    /**
     * Complete card payment after successful OTP challenge confirmation.
     */
    @Transactional
    public Transfer completeCardChallenge(Transfer transfer, Card card, String clientIp) {
        log.info("Completing card challenge authorization for transfer id={} ref={}",
                transfer.getId(), transfer.getReference());

        CardAuthRequest authReq = new CardAuthRequest(
                card.cardToken(),
                transfer.getSender().getId(),
                transfer.getAmount(),
                transfer.getCurrency() != null ? transfer.getCurrency() : "LKR",
                transfer.getReference()
        );

        CardAuthResponse authResp = cardProvider.authorize(authReq);

        if (authResp.status() == CardAuthStatus.APPROVED) {
            TransactionEvent authEvent = transfer.recordCardAuthorized(authResp.authCode());
            persistTransition(transfer, authEvent);

            CardAuthResponse capResp = cardProvider.capture(authResp.authCode(), transfer.getReference());
            if (capResp != null && capResp.approved()) {
                TransactionEvent capEvent = transfer.recordCardCaptured(authResp.authCode(), authResp.authCode());
                persistTransition(transfer, capEvent);

                if (cardDailyLimitService != null) {
                    cardDailyLimitService.commit(card, transfer.getAmount());
                }
                if (cardFraudService != null) {
                    cardFraudService.recordSuccess(card.id());
                }

                auditService.log(transfer.getSender().getId(), "CARD_TRANSFER_COMPLETED_AFTER_CHALLENGE",
                        "TRANSFER", transfer.getId(), "{\"authCode\":\"" + authResp.authCode() + "\"}", clientIp);

                if (notificationService != null) {
                    try {
                        notificationService.notifyPaymentSent(transfer);
                        notificationService.notifyPaymentReceived(transfer);
                    } catch (Exception ex) {
                        log.error("Notification delivery failure for transfer ref={}: {}",
                                transfer.getReference(), ex.getMessage());
                    }
                }
            } else {
                TransactionEvent reconEvent = transfer.requiresReconciliation(authResp.authCode());
                persistTransition(transfer, reconEvent);
            }
            return transfer;
        }

        if (cardDailyLimitService != null) {
            cardDailyLimitService.release(card, transfer.getAmount());
        }

        String safeCode = mapDeclineCode(authResp);
        String safeMessage = mapDeclineMessage(safeCode);

        TransactionEvent failEvent = transfer.recordCardDeclined(safeCode);
        persistTransition(transfer, failEvent);

        throw new UnprocessableEntityException(safeCode, safeMessage);
    }

    private String mapDeclineCode(CardAuthResponse resp) {
        if (resp == null) return "CARD_DECLINED";
        String r = resp.declineReason();
        if (resp.scenario() == CardScenario.INSUFFICIENT_FUNDS
                || (r != null && r.toLowerCase().contains("insufficient"))) {
            return "INSUFFICIENT_FUNDS";
        }
        if (resp.scenario() == CardScenario.CARD_EXPIRED
                || (r != null && r.toLowerCase().contains("expired"))) {
            return "CARD_EXPIRED";
        }
        if (resp.scenario() == CardScenario.HARD_DECLINE
                || (r != null && r.toLowerCase().contains("blocked"))) {
            return "CARD_BLOCKED";
        }
        return "CARD_DECLINED";
    }

    private String mapDeclineMessage(String code) {
        return switch (code) {
            case "INSUFFICIENT_FUNDS" -> "Card issuer declined the transaction: Insufficient funds available";
            case "CARD_EXPIRED"      -> "Card issuer declined the transaction: Card has expired";
            case "CARD_BLOCKED"      -> "Card issuer declined the transaction: Card is blocked";
            case "LIMIT_EXCEEDED"    -> "Card issuer declined the transaction: Spending limit exceeded";
            default                  -> "Card issuer declined the transaction";
        };
    }

    private void persistTransition(Transfer transfer, TransactionEvent event) {
        if (transactionStore != null && transfer.getId() != null) {
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
