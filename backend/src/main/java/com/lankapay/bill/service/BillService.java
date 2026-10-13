package com.lankapay.bill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.bill.adapter.*;
import com.lankapay.bill.crypto.BillerReferenceEncryptor;
import com.lankapay.bill.domain.BillPaymentRecord;
import com.lankapay.bill.domain.BillPaymentStore;
import com.lankapay.bill.web.dto.*;
import com.lankapay.card.domain.Card;
import com.lankapay.card.domain.CardStatus;
import com.lankapay.card.domain.CardStore;
import com.lankapay.common.exception.*;
import com.lankapay.feature.config.FeatureProperties;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.notification.domain.NotificationType;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.transfer.domain.*;
import com.lankapay.transfer.service.CardDailyLimitService;
import com.lankapay.transfer.service.DailyLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class BillService {

    private static final Logger log = LoggerFactory.getLogger(BillService.class);

    private final BillerAdapterRegistry billerAdapterRegistry;
    private final BillPaymentStore billPaymentStore;
    private final SavedBillerService savedBillerService;
    private final BillerReferenceEncryptor encryptor;
    private final TransactionStore transactionStore;
    private final DailyLimitService dailyLimitService;
    private final CardDailyLimitService cardDailyLimitService;
    private final LinkedAccountStore linkedAccountStore;
    private final CardStore cardStore;
    private final UserStore userStore;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final FeatureProperties featureProperties;
    private final ObjectMapper objectMapper;

    @Autowired
    public BillService(
            BillerAdapterRegistry billerAdapterRegistry,
            BillPaymentStore billPaymentStore,
            SavedBillerService savedBillerService,
            BillerReferenceEncryptor encryptor,
            @Autowired(required = false) TransactionStore transactionStore,
            DailyLimitService dailyLimitService,
            @Autowired(required = false) CardDailyLimitService cardDailyLimitService,
            @Autowired(required = false) LinkedAccountStore linkedAccountStore,
            @Autowired(required = false) CardStore cardStore,
            UserStore userStore,
            PasswordEncoder passwordEncoder,
            NotificationService notificationService,
            AuditService auditService,
            FeatureProperties featureProperties,
            ObjectMapper objectMapper
    ) {
        this.billerAdapterRegistry = billerAdapterRegistry;
        this.billPaymentStore = billPaymentStore;
        this.savedBillerService = savedBillerService;
        this.encryptor = encryptor;
        this.transactionStore = transactionStore;
        this.dailyLimitService = dailyLimitService;
        this.cardDailyLimitService = cardDailyLimitService;
        this.linkedAccountStore = linkedAccountStore;
        this.cardStore = cardStore;
        this.userStore = userStore;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.featureProperties = featureProperties;
        this.objectMapper = objectMapper;
    }

    public List<BillerInfo> listBillers(BillerCategory category, String query) {
        List<BillerInfo> all = billerAdapterRegistry.listAllBillers();
        return all.stream()
                .filter(b -> category == null || b.category() == category)
                .filter(b -> query == null || query.isBlank() ||
                        b.name().toLowerCase().contains(query.trim().toLowerCase()) ||
                        b.id().toLowerCase().contains(query.trim().toLowerCase()))
                .toList();
    }

    public BillerInfo getBiller(String billerId) {
        BillerAdapter adapter = billerAdapterRegistry.getAdapterForBiller(billerId);
        return adapter.getBiller(billerId)
                .orElseThrow(() -> new ResourceNotFoundException("BILLER_NOT_FOUND", "Biller not found: " + billerId));
    }

    public BillLookupResponse lookupBill(String billerId, String customerReference) {
        BillerAdapter adapter = billerAdapterRegistry.getAdapterForBiller(billerId);
        BillerInfo biller = adapter.getBiller(billerId)
                .orElseThrow(() -> new ResourceNotFoundException("BILLER_NOT_FOUND", "Biller not found: " + billerId));

        BillDetails details = adapter.fetchBill(biller.id(), customerReference);
        String masked = BillerReferenceEncryptor.mask(customerReference);
        BigDecimal fee = biller.serviceFee() != null ? biller.serviceFee() : BigDecimal.ZERO;
        BigDecimal total = details.amountDue() != null ? details.amountDue().add(fee) : fee;

        return new BillLookupResponse(
                biller.id(),
                biller.name(),
                biller.category(),
                customerReference,
                masked,
                details.customerName(),
                details.amountDue(),
                details.minimumAmount(),
                details.dueDate(),
                details.billStatus(),
                details.statusMessage(),
                fee,
                total
        );
    }

    @Transactional
    public BillPaymentReceiptResponse payBill(
            User user,
            PayBillRequest request,
            String idempotencyKey,
            String stepUpPinHeader,
            String clientIp
    ) {
        // 1. Step-up Authentication check
        String pinToVerify = (request.pin() != null && !request.pin().isBlank()) ? request.pin() : stepUpPinHeader;
        verifyStepUpPin(user.getId(), pinToVerify);

        // 2. Idempotency Check
        String payloadHash = hashPayload(request);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<BillPaymentRecord> existing = billPaymentStore.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                BillPaymentRecord rec = existing.get();
                // Check payload match
                String existingHash = hashPayload(rec);
                if (!payloadHash.equals(existingHash) && !idempotencyKey.equals(rec.idempotencyKey())) {
                    throw new ConflictException("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD",
                            "Idempotency key was previously used with a different request payload");
                }
                log.info("Idempotent bill payment replay detected for key={}", idempotencyKey);
                return toReceipt(rec);
            }
        }

        // 3. Resolve Biller
        BillerAdapter adapter = billerAdapterRegistry.getAdapterForBiller(request.billerId());
        BillerInfo biller = adapter.getBiller(request.billerId())
                .orElseThrow(() -> new ResourceNotFoundException("BILLER_NOT_FOUND", "Biller not found: " + request.billerId()));

        BigDecimal amount = request.amount();
        if (amount == null || amount.compareTo(BigDecimal.ONE) < 0) {
            throw new ValidationException("INVALID_AMOUNT", "Payment amount must be at least 1.00 LKR");
        }

        BigDecimal fee = biller.serviceFee() != null ? biller.serviceFee() : BigDecimal.ZERO;
        BigDecimal totalAmount = amount.add(fee);
        String currency = (request.currency() != null && !request.currency().isBlank()) ? request.currency().toUpperCase() : "LKR";

        // 4. Resolve and Validate Funding Source & Enforce Risk Limits
        final FundingType fundingType = request.fundingSource() != null ? request.fundingSource().type() : FundingType.ACCOUNT;
        final Long fundingSourceId = request.fundingSource() != null ? request.fundingSource().id() : null;
        String fundingLabel = "Bank Account";

        Card card = null;
        if (fundingType == FundingType.CARD) {
            if (fundingSourceId == null) {
                throw new ValidationException("CARD_ID_REQUIRED", "Card ID is required for CARD funding source");
            }
            if (cardStore == null) {
                throw new LankaPayException("CARD_SERVICE_UNAVAILABLE", "Card payment service is not configured");
            }
            card = cardStore.findByIdAndUserId(fundingSourceId, user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("CARD_NOT_FOUND", "Card not found with id: " + fundingSourceId));

            if (card.status() == CardStatus.FROZEN) {
                throw new ConflictException("CARD_FROZEN", "Card is frozen");
            }
            if (card.status() == CardStatus.EXPIRED) {
                throw new ConflictException("CARD_EXPIRED", "Card has expired");
            }
            if (card.status() != CardStatus.ACTIVE) {
                throw new ConflictException("CARD_NOT_ACTIVE", "Card status is: " + card.status());
            }

            // Reserve card limits atomically
            if (cardDailyLimitService != null) {
                cardDailyLimitService.reserve(card, totalAmount);
            }

            fundingLabel = (card.brandLabel() != null ? card.brandLabel() : "Card") + " •••• " + card.last4();
        } else {
            // Bank Account funding
            if (fundingSourceId != null && linkedAccountStore != null) {
                LinkedAccount account = linkedAccountStore.findAccountByIdAndUserId(fundingSourceId, user.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Bank account not found: " + fundingSourceId));
                if (!account.isActive()) {
                    throw new ConflictException("ACCOUNT_NOT_ACTIVE", "Bank account status is: " + account.status());
                }
                fundingLabel = account.bankCode() + " (" + account.maskedNumber() + ")";
            }

            // Enforce daily limit
            dailyLimitService.checkLimit(user, totalAmount);
        }

        // 5. Initialize Payment Transaction & State Machine
        String paymentReference = UUID.randomUUID().toString();
        TransactionStatus status = TransactionStatus.PENDING;

        // Transition: PENDING -> PROCESSING
        status = status.transitionTo(TransactionStatus.PROCESSING);

        // 6. Invoke BillerAdapter
        BillPaymentRequest adapterReq = new BillPaymentRequest(
                paymentReference,
                biller.id(),
                request.customerReference(),
                amount,
                currency,
                fundingType,
                fundingSourceId,
                user.getFullName()
        );

        BillPaymentResponse adapterResp;
        try {
            adapterResp = adapter.payBill(adapterReq);
        } catch (Exception ex) {
            log.error("Biller adapter threw unexpected exception during payment: {}", ex.getMessage());
            // A timeout or communication breakdown moves the transaction to REQUIRES_RECONCILIATION, NEVER FAILED!
            adapterResp = new BillPaymentResponse(
                    paymentReference,
                    null,
                    TransactionStatus.REQUIRES_RECONCILIATION,
                    "BILLER_TIMEOUT: Adapter communication exception: " + ex.getMessage(),
                    amount,
                    Instant.now()
            );
        }

        // Transition based on adapter response:
        if (adapterResp.status() == TransactionStatus.COMPLETED) {
            status = status.transitionTo(TransactionStatus.COMPLETED);
            // Record spend towards daily limits
            if (fundingType == FundingType.CARD && cardDailyLimitService != null && card != null) {
                cardDailyLimitService.commit(card, totalAmount);
            } else {
                dailyLimitService.recordSpend(user, totalAmount);
            }
        } else if (adapterResp.status() == TransactionStatus.REQUIRES_RECONCILIATION) {
            // State machine legal transition: PROCESSING -> REQUIRES_RECONCILIATION
            status = status.transitionTo(TransactionStatus.REQUIRES_RECONCILIATION);
        } else if (adapterResp.status() == TransactionStatus.FAILED) {
            status = status.transitionTo(TransactionStatus.FAILED);
            if (fundingType == FundingType.CARD && cardDailyLimitService != null && card != null) {
                cardDailyLimitService.release(card, totalAmount);
            }
        } else if (adapterResp.status() == TransactionStatus.PENDING) {
            // Keep status pending/processing
            status = TransactionStatus.PENDING;
        }

        String maskedRef = BillerReferenceEncryptor.mask(request.customerReference());
        String encryptedRef = encryptor.encrypt(request.customerReference());

        // 7. Persist to TransactionStore (if present) for cross-superapp visibility
        Long transactionId = null;
        if (transactionStore != null) {
            TransactionRecord txRecord = new TransactionRecord(
                    null,
                    paymentReference,
                    idempotencyKey,
                    user.getId(),
                    user.getPhone(),
                    user.getFullName(),
                    0L,
                    biller.id(),
                    biller.name(),
                    null,
                    biller.id(),
                    amount,
                    currency,
                    status,
                    adapterResp.failureReason(),
                    adapterResp.billerPaymentRef(),
                    paymentReference,
                    request.note() != null ? request.note() : ("Bill Payment: " + biller.name()),
                    true,
                    TransferInitiatedVia.BILL_PAYMENT,
                    null,
                    1L,
                    Instant.now(),
                    Instant.now(),
                    fundingType,
                    fundingType == FundingType.CARD ? fundingSourceId : null,
                    null,
                    null,
                    fee,
                    totalAmount,
                    null,
                    0
            );
            TransactionRecord savedTx = transactionStore.saveTransfer(txRecord);
            transactionId = savedTx != null ? savedTx.id() : null;
        }

        // 8. Persist to BillPaymentStore
        BillPaymentRecord billPaymentRecord = new BillPaymentRecord(
                null,
                paymentReference,
                idempotencyKey,
                user.getId(),
                biller.id(),
                biller.name(),
                biller.category(),
                encryptedRef,
                maskedRef,
                user.getFullName(),
                amount,
                fee,
                totalAmount,
                currency,
                status,
                adapterResp.failureReason(),
                adapterResp.billerPaymentRef(),
                fundingType,
                fundingSourceId,
                fundingLabel,
                LocalDate.now().plusDays(14),
                transactionId,
                null,
                null
        );
        BillPaymentRecord savedPayment = billPaymentStore.save(billPaymentRecord);

        // 9. Save biller if requested
        if (request.saveBiller() && request.billerNickname() != null && !request.billerNickname().isBlank()) {
            try {
                savedBillerService.saveBiller(
                        user.getId(),
                        new SavedBillerRequest(biller.id(), request.customerReference(), request.billerNickname()),
                        clientIp
                );
            } catch (Exception ex) {
                log.info("Auto-save biller skipped or already saved: {}", ex.getMessage());
            }
        }

        // 10. Audit logging & Lock-Screen Safe Notifications
        auditService.log(user.getId(), "BILL_PAYMENT_INITIATED", "BILL_PAYMENT", savedPayment.id(),
                "{\"billerId\":\"" + biller.id() + "\",\"status\":\"" + status + "\"}", clientIp);

        if (status == TransactionStatus.COMPLETED) {
            auditService.log(user.getId(), "BILL_PAYMENT_COMPLETED", "BILL_PAYMENT", savedPayment.id(), null, clientIp);
            // Strict lock-screen safe message: NO amounts, NO account numbers
            notificationService.dispatch(
                    user,
                    NotificationType.PAYMENT_SENT,
                    "Payment Sent",
                    "Bill payment sent to " + biller.name() + ". Tap to view receipt."
            );

            // Rewards hook: simulate rewards points event if feature is enabled
            if (featureProperties != null && featureProperties.getFlags() != null &&
                    Boolean.TRUE.equals(featureProperties.getFlags().get("rewards"))) {
                log.info("Simulated rewards points event emitted for user={} amount={}", user.getId(), amount);
            }
        } else if (status == TransactionStatus.FAILED) {
            notificationService.dispatch(
                    user,
                    NotificationType.PAYMENT_FAILED,
                    "Payment Failed",
                    "Your bill payment to " + biller.name() + " could not be completed."
            );
        }

        log.info("Bill payment processed userId={} billerId={} ref={} status={}",
                user.getId(), biller.id(), paymentReference, status);

        return toReceipt(savedPayment);
    }

    @Transactional
    public BillPaymentReceiptResponse reconcilePayment(Long paymentId, Long userId) {
        BillPaymentRecord record = requireOwnedPayment(paymentId, userId);

        if (record.status().isTerminal()) {
            return toReceipt(record);
        }

        BillerAdapter adapter = billerAdapterRegistry.getAdapterForBiller(record.billerId());
        BillPaymentResponse queryResp = adapter.queryPayment(record.paymentReference());

        TransactionStatus newStatus = record.status();
        if (queryResp.status() == TransactionStatus.COMPLETED && record.status() == TransactionStatus.REQUIRES_RECONCILIATION) {
            newStatus = record.status().transitionTo(TransactionStatus.COMPLETED);
        } else if (queryResp.status() == TransactionStatus.REVERSED && record.status() == TransactionStatus.REQUIRES_RECONCILIATION) {
            newStatus = record.status().transitionTo(TransactionStatus.REVERSED);
        } else if (queryResp.status() == TransactionStatus.FAILED && record.status() == TransactionStatus.REQUIRES_RECONCILIATION) {
            newStatus = record.status().transitionTo(TransactionStatus.FAILED);
        }

        final TransactionStatus finalStatus = newStatus;
        BillPaymentRecord updated = record.withBillerConfirmation(queryResp.billerPaymentRef(), finalStatus);
        billPaymentStore.save(updated);

        // Update transaction store if present
        if (transactionStore != null && record.transactionId() != null) {
            transactionStore.findTransferById(record.transactionId()).ifPresent(tx -> {
                TransactionRecord updatedTx = new TransactionRecord(
                        tx.id(), tx.reference(), tx.idempotencyKey(), tx.senderId(), tx.senderPhone(), tx.senderFullName(),
                        tx.receiverId(), tx.receiverPhone(), tx.receiverFullName(), tx.senderBankCode(), tx.receiverBankCode(),
                        tx.amount(), tx.currency(), finalStatus, queryResp.failureReason(), queryResp.billerPaymentRef(),
                        tx.providerReference(), tx.note(), tx.simulated(), tx.initiatedVia(), tx.qrCodeId(), tx.version() + 1,
                        tx.createdAt(), Instant.now(), tx.fundingType(), tx.cardId(), tx.cardLast4(), tx.cardKind(),
                        tx.fee(), tx.totalAmount(), tx.challengeRef(), tx.challengeAttempts()
                );
                transactionStore.saveTransfer(updatedTx);
            });
        }

        return toReceipt(updated);
    }

    public BillPaymentReceiptResponse getPaymentById(Long paymentId, Long userId) {
        BillPaymentRecord record = requireOwnedPayment(paymentId, userId);
        return toReceipt(record);
    }

    public List<BillPaymentReceiptResponse> listPayments(Long userId, int offset, int limit) {
        return billPaymentStore.findByUserId(userId, offset, limit).stream()
                .map(this::toReceipt)
                .toList();
    }

    private BillPaymentRecord requireOwnedPayment(Long paymentId, Long userId) {
        BillPaymentRecord record = billPaymentStore.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("BILL_PAYMENT_NOT_FOUND", "Bill payment not found: " + paymentId));
        if (userId != null && !userId.equals(record.userId())) {
            throw new AuthorizationException("FORBIDDEN", "You do not have permission to view payment: " + paymentId);
        }
        return record;
    }

    private void verifyStepUpPin(Long userId, String pin) {
        if (pin == null || pin.isBlank()) {
            throw new AuthorizationException("STEP_UP_AUTH_REQUIRED", "Step-up authentication required: PIN must be provided");
        }
        UserAccount account = userStore.findUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER", userId));
        if (!passwordEncoder.matches(pin, account.passwordHash())) {
            throw new InvalidCredentialsException("INVALID_PIN", "Invalid PIN provided for step-up authentication");
        }
    }

    private String hashPayload(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private BillPaymentReceiptResponse toReceipt(BillPaymentRecord record) {
        String receiptNumber = "REC-BILL-" + record.paymentReference().substring(0, Math.min(8, record.paymentReference().length())).toUpperCase();
        return new BillPaymentReceiptResponse(
                receiptNumber,
                record.id(),
                record.paymentReference(),
                record.billerId(),
                record.billerName(),
                record.category(),
                record.customerReferenceMasked(),
                record.customerName(),
                record.amount(),
                record.fee(),
                record.totalAmount(),
                record.currency(),
                record.status(),
                record.billerConfirmationRef(),
                record.failureReason(),
                record.fundingType(),
                record.fundingLabel(),
                record.billDueDate(),
                record.createdAt()
        );
    }
}
