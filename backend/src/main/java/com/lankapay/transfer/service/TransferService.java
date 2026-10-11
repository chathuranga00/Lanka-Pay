package com.lankapay.transfer.service;

import com.lankapay.admin.service.RiskManagementService;
import com.lankapay.bank.domain.BankAccount;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.InvalidCredentialsException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.UnprocessableEntityException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.transfer.domain.TransactionRecord;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.TransactionStore;
import com.lankapay.transfer.domain.Transfer;
import com.lankapay.transfer.domain.TransferInitiatedVia;
import com.lankapay.transfer.web.dto.InitiateTransferRequest;
import com.lankapay.transfer.web.dto.QuoteRequest;
import com.lankapay.transfer.web.dto.QuoteResponse;
import com.lankapay.transfer.web.dto.ReceiptResponse;
import com.lankapay.transfer.web.dto.TransferDirection;
import com.lankapay.transfer.web.dto.TransferResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransactionStore transactionStore;
    private final UserStore userStore;
    private final LinkedAccountStore linkedAccountStore;
    private final TransferProcessor transferProcessor;
    private final DailyLimitService dailyLimitService;
    private final IdempotencyService idempotencyService;
    private final PasswordEncoder passwordEncoder;
    private final RiskManagementService riskManagementService;

    @Autowired
    public TransferService(TransactionStore transactionStore,
                           UserStore userStore,
                           LinkedAccountStore linkedAccountStore,
                           TransferProcessor transferProcessor,
                           DailyLimitService dailyLimitService,
                           IdempotencyService idempotencyService,
                           PasswordEncoder passwordEncoder,
                           @Autowired(required = false) RiskManagementService riskManagementService) {
        this.transactionStore = transactionStore;
        this.userStore = userStore;
        this.linkedAccountStore = linkedAccountStore;
        this.transferProcessor = transferProcessor;
        this.dailyLimitService = dailyLimitService;
        this.idempotencyService = idempotencyService;
        this.passwordEncoder = passwordEncoder;
        this.riskManagementService = riskManagementService;
    }

    /**
     * Generate quote for proposed transfer.
     */
    @Transactional(readOnly = true)
    public QuoteResponse getQuote(QuoteRequest request, User sender) {
        User payee = resolvePayee(request.payeeId(), request.payeePhone());
        if (payee.getId().equals(sender.getId())) {
            throw new ValidationException("CANNOT_TRANSFER_TO_SELF", "Sender and payee cannot be the same user");
        }

        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal totalDebit = request.amount().add(fee);
        String quoteRef = "QUO-" + UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(300);

        return new QuoteResponse(
                quoteRef,
                payee.getId(),
                payee.getFullName(),
                maskPhone(payee.getPhone()),
                request.amount(),
                fee,
                totalDebit,
                request.currency() != null ? request.currency() : "LKR",
                expiresAt,
                true
        );
    }

    /**
     * Execute transfer with step-up PIN verification, idempotency checking,
     * daily limit checks, pre-flight checks, and audit events.
     */
    @Transactional
    public TransferResponse initiateTransfer(String idempotencyKey,
                                            String stepUpPinHeader,
                                            InitiateTransferRequest request,
                                            User sender,
                                            String clientIp) {
        String pin = (stepUpPinHeader != null && !stepUpPinHeader.isBlank()) ? stepUpPinHeader : request.pin();
        return initiateTransfer(request, sender, pin, idempotencyKey, clientIp);
    }

    @Transactional
    public TransferResponse initiateTransfer(InitiateTransferRequest request,
                                            User sender,
                                            String pin,
                                            String idempotencyKey,
                                            String clientIp) {

        // 1. Step-up authentication: verify sender PIN before executing financial debit
        if (pin == null || pin.isBlank()) {
            log.warn("Step-up authentication failed: PIN is missing for user id={}", sender.getId());
            throw new AuthorizationException("STEP_UP_AUTH_REQUIRED", "Step-up authentication required: PIN must be provided");
        }
        if (!passwordEncoder.matches(pin, sender.getPasswordHash())) {
            log.warn("Step-up authentication failed for user id={}", sender.getId());
            throw new InvalidCredentialsException("INVALID_PIN", "Invalid PIN provided for step-up authentication");
        }

        // 2. Idempotency check: same key + same payload -> return cached; same key + diff payload -> 409
        String payloadHash = idempotencyService.hashPayload(request);
        Optional<TransferResponse> existing = idempotencyService.findExistingResponse(idempotencyKey, payloadHash);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Also check if transfer already exists with this idempotency key in Firestore
        Optional<TransactionRecord> existingTx = transactionStore.findTransferByIdempotencyKey(idempotencyKey);
        if (existingTx.isPresent()) {
            TransactionRecord tx = existingTx.get();
            if (tx.status() == TransactionStatus.PENDING) {
                tx = awaitTransferCompletion(idempotencyKey, tx);
            }
            TransferResponse response = TransferResponse.from(tx);
            if (tx.status() != TransactionStatus.PENDING) {
                idempotencyService.record(idempotencyKey, payloadHash, tx.id(), response, 200);
            }
            return response;
        }

        // 3. Resolve payee
        User payee = resolvePayee(request.payeeId(), request.payeePhone());
        if (payee.getId().equals(sender.getId())) {
            throw new ValidationException("CANNOT_TRANSFER_TO_SELF", "Sender and payee cannot be the same user");
        }

        // 4. Verify risk rules & daily limit
        if (riskManagementService != null) {
            riskManagementService.evaluateTransferRisk(sender, request.amount());
        }

        dailyLimitService.checkLimit(sender, request.amount());

        // 5. Verify bank accounts
        BankAccount senderAccount = resolveSenderAccount(sender.getId());
        BankAccount payeeAccount = resolvePayeeAccount(payee.getId());

        // 6. Create Transfer entity in PENDING state
        String reference = UUID.randomUUID().toString();
        Transfer transfer = new Transfer(
                reference,
                sender,
                payee,
                request.amount(),
                request.currency() != null ? request.currency() : "LKR",
                TransferInitiatedVia.APP
        );
        transfer.setIdempotencyKey(idempotencyKey);
        transfer.setNote(request.note());
        transfer.setSenderBankCode(senderAccount.getBankCode());
        transfer.setReceiverBankCode(payeeAccount.getBankCode());

        var txRecord = new TransactionRecord(
                null,
                reference,
                idempotencyKey,
                sender.getId(),
                sender.getPhone(),
                sender.getFullName(),
                payee.getId(),
                payee.getPhone(),
                payee.getFullName(),
                senderAccount.getBankCode(),
                payeeAccount.getBankCode(),
                request.amount(),
                request.currency() != null ? request.currency() : "LKR",
                TransactionStatus.PENDING,
                null,
                null,
                null,
                request.note(),
                true,
                TransferInitiatedVia.APP,
                null,
                1L,
                Instant.now(),
                Instant.now()
        );
        var createdRecord = transactionStore.createTransferWithIdempotency(txRecord, idempotencyKey, payloadHash);
        transfer.setId(createdRecord.id());
        transfer.setVersion(createdRecord.version());

        if (createdRecord.reference() != null && !createdRecord.reference().equals(reference)) {
            // Another thread won the race and created this transfer
            TransactionRecord tx = createdRecord;
            if (tx.status() == TransactionStatus.PENDING) {
                tx = awaitTransferCompletion(idempotencyKey, tx);
            }
            TransferResponse response = TransferResponse.from(tx);
            if (tx.status() != TransactionStatus.PENDING) {
                idempotencyService.record(idempotencyKey, payloadHash, tx.id(), response, 200);
            }
            return response;
        }

        if (createdRecord.status() == TransactionStatus.COMPLETED) {
            TransferResponse response = TransferResponse.from(createdRecord);
            idempotencyService.record(idempotencyKey, payloadHash, createdRecord.id(), response, 200);
            return response;
        }

        // 7. Process via BankAdapter through TransferProcessor
        transfer = transferProcessor.process(transfer, senderAccount, payeeAccount, request.quoteReference(), clientIp);

        // 8. Build response and record idempotency cache
        TransferResponse response = TransferResponse.from(transfer);
        idempotencyService.record(idempotencyKey, payloadHash, transfer.getId(), response, 200);

        return response;
    }

    private BankAccount resolveSenderAccount(Long senderId) {
        var activeDefault = linkedAccountStore.findActiveDefaultAccount(senderId);
        if (activeDefault.isPresent()) {
            return toBankAccountEntity(activeDefault.get());
        }
        var allActive = linkedAccountStore.findAccountsByUserIdAndStatus(senderId, BankAccountStatus.ACTIVE);
        if (!allActive.isEmpty()) {
            return toBankAccountEntity(allActive.get(0));
        }
        throw new UnprocessableEntityException("NO_ACTIVE_BANK_ACCOUNT",
                "Sender has no active bank account linked. Please link a bank account first.");
    }

    private BankAccount resolvePayeeAccount(Long payeeId) {
        var activeDefault = linkedAccountStore.findActiveDefaultAccount(payeeId);
        if (activeDefault.isPresent()) {
            return toBankAccountEntity(activeDefault.get());
        }
        var allActive = linkedAccountStore.findAccountsByUserIdAndStatus(payeeId, BankAccountStatus.ACTIVE);
        if (!allActive.isEmpty()) {
            return toBankAccountEntity(allActive.get(0));
        }
        throw new UnprocessableEntityException("PAYEE_NO_ACTIVE_BANK_ACCOUNT",
                "Payee has no active bank account to receive funds.");
    }

    private BankAccount toBankAccountEntity(LinkedAccount la) {
        User u = new User();
        u.setId(la.userId());
        BankAccount ba = new BankAccount(u, la.bankCode(), la.accountNumberHash(),
                la.maskedNumber(), la.accountName(), la.encryptedAccountReference());
        ba.setId(la.id());
        ba.setStatus(la.status());
        ba.setDefault(la.isDefault());
        ba.setReceivingEnabled(la.receivingEnabled());
        ba.setDiscoverable(la.discoverable());
        return ba;
    }

    /**
     * Get transfer details by ID with object-level security.
     */
    @Transactional(readOnly = true)
    public TransferResponse getTransferById(Long id, Long authenticatedUserId) {
        var tx = transactionStore.findTransferById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TRANSFER_NOT_FOUND",
                        "Transfer not found with id: " + id));

        boolean isSender = tx.senderId().equals(authenticatedUserId);
        boolean isReceiver = tx.receiverId().equals(authenticatedUserId);

        if (!isSender && !isReceiver) {
            throw new AuthorizationException("FORBIDDEN",
                    "You do not have permission to access transfer id: " + id);
        }

        return TransferResponse.from(tx);
    }

    /**
     * Get transaction receipt data. Object-level check: caller must be sender or receiver.
     */
    @Transactional(readOnly = true)
    public ReceiptResponse getTransferReceipt(Long id, Long authenticatedUserId) {
        var tx = transactionStore.findTransferById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TRANSFER_NOT_FOUND",
                        "Transfer not found with id: " + id));

        boolean isSender = tx.senderId().equals(authenticatedUserId);
        boolean isReceiver = tx.receiverId().equals(authenticatedUserId);

        if (!isSender && !isReceiver) {
            throw new AuthorizationException("FORBIDDEN",
                    "You do not have permission to view receipt for transfer id: " + id);
        }

        return ReceiptResponse.from(tx);
    }

    /**
     * Get user transfer history with all 5 filters (date range, direction, bank, status, amount range) and pagination.
     */
    @Transactional(readOnly = true)
    public Page<TransferResponse> getTransfersWithFilters(Long authenticatedUserId,
                                                          TransferDirection direction,
                                                          TransactionStatus status,
                                                          Instant fromTime,
                                                          Instant toTime,
                                                          String bank,
                                                          BigDecimal minAmount,
                                                          BigDecimal maxAmount,
                                                          Pageable pageable) {

        Page<TransactionRecord> page = transactionStore.searchTransfers(
                authenticatedUserId, direction, status, fromTime, toTime, bank, minAmount, maxAmount, pageable);
        return page.map(TransferResponse::from);
    }

    /**
     * Backwards-compatible overload for existing callers.
     */
    @Transactional(readOnly = true)
    public Page<TransferResponse> getTransfers(Long authenticatedUserId,
                                              TransferDirection direction,
                                              TransactionStatus status,
                                              Instant fromTime,
                                              Instant toTime,
                                              Pageable pageable) {
        return getTransfersWithFilters(authenticatedUserId, direction, status, fromTime, toTime, null, null, null, pageable);
    }

    private User resolvePayee(Long payeeId, String payeePhone) {
        if (payeeId != null) {
            var u = userStore.findUserById(payeeId)
                    .orElseThrow(() -> new ResourceNotFoundException("PAYEE_NOT_FOUND",
                            "Payee user not found with id: " + payeeId));
            User user = new User();
            user.setId(u.id());
            user.setPhone(u.phone());
            user.setFullName(u.fullName());
            user.setPasswordHash(u.passwordHash());
            return user;
        }
        if (payeePhone != null && !payeePhone.isBlank()) {
            var u = userStore.findUserByPhone(payeePhone)
                    .orElseThrow(() -> new ResourceNotFoundException("PAYEE_NOT_FOUND",
                            "Payee user not found with phone: " + payeePhone));
            User user = new User();
            user.setId(u.id());
            user.setPhone(u.phone());
            user.setFullName(u.fullName());
            user.setPasswordHash(u.passwordHash());
            return user;
        }
        throw new ValidationException("PAYEE_IDENTIFIER_REQUIRED", "Either payeeId or payeePhone must be provided");
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        int len = phone.length();
        return phone.substring(0, 6) + "***" + phone.substring(len - 4);
    }

    private TransactionRecord awaitTransferCompletion(String idempotencyKey, TransactionRecord current) {
        long deadline = System.currentTimeMillis() + 8000;
        TransactionRecord latest = current;
        while (latest.status() == TransactionStatus.PENDING && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            Optional<TransactionRecord> updated = transactionStore.findTransferByIdempotencyKey(idempotencyKey);
            if (updated.isPresent()) {
                latest = updated.get();
            }
        }
        return latest;
    }
}
