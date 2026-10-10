package com.lankapay.transfer.service;

import com.lankapay.bank.domain.BankAccount;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.repository.BankAccountRepository;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.InvalidCredentialsException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.UnprocessableEntityException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.repository.UserRepository;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.Transfer;
import com.lankapay.transfer.domain.TransferInitiatedVia;
import com.lankapay.transfer.repository.TransferRepository;
import com.lankapay.transfer.web.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransferRepository transferRepository;
    private final BankAccountRepository bankAccountRepository;
    private final UserRepository userRepository;
    private final TransferProcessor transferProcessor;
    private final DailyLimitService dailyLimitService;
    private final IdempotencyService idempotencyService;
    private final PasswordEncoder passwordEncoder;

    public TransferService(TransferRepository transferRepository,
                           BankAccountRepository bankAccountRepository,
                           UserRepository userRepository,
                           TransferProcessor transferProcessor,
                           DailyLimitService dailyLimitService,
                           IdempotencyService idempotencyService,
                           PasswordEncoder passwordEncoder) {
        this.transferRepository = transferRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.userRepository = userRepository;
        this.transferProcessor = transferProcessor;
        this.dailyLimitService = dailyLimitService;
        this.idempotencyService = idempotencyService;
        this.passwordEncoder = passwordEncoder;
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

        dailyLimitService.checkLimit(sender, request.amount());

        BigDecimal fee = BigDecimal.ZERO.setScale(2);
        BigDecimal total = request.amount().add(fee);
        String quoteRef = "QUO-" + UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        return new QuoteResponse(
                quoteRef,
                payee.getId(),
                payee.getFullName(),
                maskPhone(payee.getPhone()),
                request.amount(),
                fee,
                total,
                request.currency() != null ? request.currency() : "LKR",
                expiresAt,
                true
        );
    }

    /**
     * Initiate transfer with Idempotency and Step-Up authentication.
     */
    @Transactional
    public TransferResponse initiateTransfer(String idempotencyKey,
                                             String stepUpPinHeader,
                                             InitiateTransferRequest request,
                                             User sender,
                                             String clientIp) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required");
        }

        // 1. Step-up authentication verification
        String pin = (stepUpPinHeader != null && !stepUpPinHeader.isBlank())
                ? stepUpPinHeader
                : request.pin();

        if (pin == null || pin.isBlank()) {
            throw new AuthorizationException("STEP_UP_AUTHENTICATION_REQUIRED",
                    "Step-up authentication required: provide 6-digit PIN via X-Step-Up-Pin header or request body");
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

        // Also check if transfer already exists with this idempotency key in DB
        Optional<Transfer> existingTransfer = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTransfer.isPresent()) {
            TransferResponse response = TransferResponse.from(existingTransfer.get());
            idempotencyService.record(idempotencyKey, payloadHash, existingTransfer.get().getId(), response, 200);
            return response;
        }

        // 3. Resolve payee
        User payee = resolvePayee(request.payeeId(), request.payeePhone());
        if (payee.getId().equals(sender.getId())) {
            throw new ValidationException("CANNOT_TRANSFER_TO_SELF", "Sender and payee cannot be the same user");
        }

        // 4. Verify daily limit
        dailyLimitService.checkLimit(sender, request.amount());

        // 5. Verify bank accounts
        BankAccount senderAccount = bankAccountRepository
                .findByUserIdAndIsDefaultTrueAndStatus(sender.getId(), BankAccountStatus.ACTIVE)
                .orElseGet(() -> bankAccountRepository
                        .findByUserIdAndStatus(sender.getId(), BankAccountStatus.ACTIVE)
                        .stream().findFirst()
                        .orElseThrow(() -> new UnprocessableEntityException("NO_ACTIVE_BANK_ACCOUNT",
                                "Sender has no active bank account linked. Please link a bank account first.")));

        BankAccount payeeAccount = bankAccountRepository
                .findByUserIdAndIsDefaultTrueAndStatus(payee.getId(), BankAccountStatus.ACTIVE)
                .orElseGet(() -> bankAccountRepository
                        .findByUserIdAndStatus(payee.getId(), BankAccountStatus.ACTIVE)
                        .stream().findFirst()
                        .orElseThrow(() -> new UnprocessableEntityException("PAYEE_NO_ACTIVE_BANK_ACCOUNT",
                                "Payee has no active bank account to receive funds.")));

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
        transfer = transferRepository.save(transfer);

        // 7. Process via BankAdapter through TransferProcessor
        transfer = transferProcessor.process(transfer, senderAccount, payeeAccount, request.quoteReference(), clientIp);

        // 8. Build response and record idempotency cache
        TransferResponse response = TransferResponse.from(transfer);
        idempotencyService.record(idempotencyKey, payloadHash, transfer.getId(), response, 200);

        return response;
    }

    /**
     * Get transfer details by ID with object-level security.
     */
    @Transactional(readOnly = true)
    public TransferResponse getTransferById(Long id, Long authenticatedUserId) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TRANSFER_NOT_FOUND",
                        "Transfer not found with id: " + id));

        boolean isSender = transfer.getSender().getId().equals(authenticatedUserId);
        boolean isReceiver = transfer.getReceiver().getId().equals(authenticatedUserId);

        if (!isSender && !isReceiver) {
            throw new AuthorizationException("FORBIDDEN",
                    "You do not have permission to access transfer id: " + id);
        }

        return TransferResponse.from(transfer);
    }

    /**
     * Get user transfer history with filters and pagination.
     */
    @Transactional(readOnly = true)
    public Page<TransferResponse> getTransfers(Long authenticatedUserId,
                                              TransferDirection direction,
                                              TransactionStatus status,
                                              Instant fromTime,
                                              Instant toTime,
                                              Pageable pageable) {

        Long senderId = null;
        Long receiverId = null;
        Long userId = authenticatedUserId;

        if (direction == TransferDirection.SENT) {
            senderId = authenticatedUserId;
            userId = null;
        } else if (direction == TransferDirection.RECEIVED) {
            receiverId = authenticatedUserId;
            userId = null;
        }

        Page<Transfer> page = transferRepository.findWithFilters(
                userId, senderId, receiverId, status, fromTime, toTime, pageable);

        return page.map(TransferResponse::from);
    }

    private User resolvePayee(Long payeeId, String payeePhone) {
        if (payeeId != null) {
            return userRepository.findById(payeeId)
                    .orElseThrow(() -> new ResourceNotFoundException("PAYEE_NOT_FOUND",
                            "Payee user not found with id: " + payeeId));
        }
        if (payeePhone != null && !payeePhone.isBlank()) {
            return userRepository.findByPhone(payeePhone)
                    .orElseThrow(() -> new ResourceNotFoundException("PAYEE_NOT_FOUND",
                            "Payee user not found with phone: " + payeePhone));
        }
        throw new ValidationException("PAYEE_IDENTIFIER_REQUIRED", "Either payeeId or payeePhone must be provided");
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        int len = phone.length();
        return phone.substring(0, 6) + "***" + phone.substring(len - 4);
    }
}
