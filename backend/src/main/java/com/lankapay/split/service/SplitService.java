package com.lankapay.split.service;

import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.common.util.PhoneNumberNormalizer;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.notification.domain.NotificationType;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.qr.domain.QrStore;
import com.lankapay.qr.service.PaymentRequestService;
import com.lankapay.qr.web.dto.CreatePaymentRequestDto;
import com.lankapay.qr.web.dto.PaymentRequestResponse;
import com.lankapay.split.domain.*;
import com.lankapay.split.web.dto.*;
import com.lankapay.transfer.service.TransferService;
import com.lankapay.transfer.web.dto.InitiateTransferRequest;
import com.lankapay.transfer.web.dto.TransferResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class SplitService {

    private static final Logger log = LoggerFactory.getLogger(SplitService.class);

    private final SplitStore splitStore;
    private final UserStore userStore;
    private final PaymentRequestService paymentRequestService;
    private final TransferService transferService;
    private final NotificationService notificationService;
    private final QrStore qrStore;

    @Autowired
    public SplitService(
            SplitStore splitStore,
            UserStore userStore,
            PaymentRequestService paymentRequestService,
            TransferService transferService,
            @Autowired(required = false) NotificationService notificationService,
            @Autowired(required = false) QrStore qrStore
    ) {
        this.splitStore = splitStore;
        this.userStore = userStore;
        this.paymentRequestService = paymentRequestService;
        this.transferService = transferService;
        this.notificationService = notificationService;
        this.qrStore = qrStore;
    }

    /**
     * Create a bill split with integer arithmetic share distribution.
     * Any rounding remainder goes to the owner.
     * One payment request is created per participant reusing payment-request single-use & expiry rules.
     */
    @Transactional
    public SplitResponse createSplit(CreateSplitRequest request, User owner) {
        if (request.totalMinor() == null || request.totalMinor() <= 0) {
            throw new ValidationException("INVALID_TOTAL", "Total bill amount must be greater than zero");
        }

        if (request.title() == null || request.title().isBlank()) {
            throw new ValidationException("TITLE_REQUIRED", "Split title cannot be empty");
        }

        if (request.participants() == null || request.participants().isEmpty()) {
            throw new ValidationException("PARTICIPANTS_REQUIRED", "At least one participant is required");
        }

        if (request.participants().size() > 10) {
            throw new ValidationException("MAX_PARTICIPANTS_EXCEEDED", "A maximum of 10 participants is allowed per split");
        }

        String mode = request.mode() != null ? request.mode().toUpperCase() : "EQUAL";
        int validityHours = request.validityHours() != null && request.validityHours() > 0 ? request.validityHours() : 48;
        Instant expiresAt = Instant.now().plus(validityHours, ChronoUnit.HOURS);
        int validityMinutes = validityHours * 60;

        // Resolve and validate all participants
        List<User> resolvedParticipants = new ArrayList<>();
        Set<Long> seenUserIds = new HashSet<>();

        for (SplitParticipantRequest pReq : request.participants()) {
            User participant = resolveUser(pReq);
            if (participant.getId().equals(owner.getId())) {
                throw new ValidationException("CANNOT_SPLIT_WITH_SELF", "Owner cannot be included in participant list");
            }
            if (!seenUserIds.add(participant.getId())) {
                throw new ValidationException("DUPLICATE_PARTICIPANT", "Duplicate participant in split: " + participant.getId());
            }
            resolvedParticipants.add(participant);
        }

        // Integer arithmetic for equal or custom shares
        long totalMinor = request.totalMinor();
        long ownerShareMinor;
        List<SplitParticipant> participantEntities = new ArrayList<>();

        if ("EQUAL".equals(mode)) {
            // Total people = participants + owner
            int totalPeople = resolvedParticipants.size() + 1;
            long baseShareMinor = totalMinor / totalPeople;
            long remainder = totalMinor - (baseShareMinor * totalPeople);

            ownerShareMinor = baseShareMinor + remainder;

            for (User pUser : resolvedParticipants) {
                SplitParticipant p = new SplitParticipant(
                        pUser.getId(),
                        pUser.getPhone(),
                        pUser.getFullName(),
                        baseShareMinor
                );
                participantEntities.add(p);
            }
        } else if ("CUSTOM".equals(mode)) {
            long participantSum = 0L;
            for (int i = 0; i < resolvedParticipants.size(); i++) {
                User pUser = resolvedParticipants.get(i);
                SplitParticipantRequest pReq = request.participants().get(i);
                if (pReq.shareMinor() == null || pReq.shareMinor() <= 0) {
                    throw new ValidationException("INVALID_SHARE", "Each participant must have a positive shareMinor in custom mode");
                }
                participantSum += pReq.shareMinor();

                SplitParticipant p = new SplitParticipant(
                        pUser.getId(),
                        pUser.getPhone(),
                        pUser.getFullName(),
                        pReq.shareMinor()
                );
                participantEntities.add(p);
            }

            if (participantSum > totalMinor) {
                throw new ValidationException("INVALID_SHARES_SUM", "Sum of participant shares cannot exceed total bill amount");
            }
            ownerShareMinor = totalMinor - participantSum;
        } else {
            throw new ValidationException("INVALID_SPLIT_MODE", "Split mode must be either EQUAL or CUSTOM");
        }

        // Verify exact sum check
        long sumAll = ownerShareMinor + participantEntities.stream().mapToLong(SplitParticipant::getShareMinor).sum();
        if (sumAll != totalMinor) {
            throw new ValidationException("SHARES_DO_NOT_SUM_TO_TOTAL", "Shares sum (" + sumAll + ") must equal totalMinor (" + totalMinor + ")");
        }

        // Create one PaymentRequest per participant using existing payment-request module
        for (int i = 0; i < participantEntities.size(); i++) {
            SplitParticipant p = participantEntities.get(i);
            User pUser = resolvedParticipants.get(i);

            BigDecimal amount = BigDecimal.valueOf(p.getShareMinor(), 2);
            CreatePaymentRequestDto prDto = new CreatePaymentRequestDto(
                    amount,
                    "LKR",
                    pUser.getId(),
                    pUser.getPhone(),
                    "Split: " + request.title(),
                    validityMinutes
            );

            try {
                PaymentRequestResponse prResp = paymentRequestService.createPaymentRequest(prDto, owner);
                p.setPaymentRequestId(prResp.id());
                p.setPaymentRequestReference(prResp.reference());
                p.setQrReference(prResp.qrReference());
                p.setQrPayload(prResp.qrPayload());
            } catch (Exception ex) {
                log.warn("Payment request creation fallback for participant {}: {}", pUser.getId(), ex.getMessage());
                p.setPaymentRequestReference("PREQ-SPLIT-" + UUID.randomUUID());
            }

            // Dispatch privacy-safe invitation notification
            dispatchPrivacySafeNotification(
                    pUser,
                    "Bill Split Invitation",
                    "You were added to a bill split by " + owner.getFullName() + ". Open app to review."
            );
        }

        BillSplit split = new BillSplit(
                null,
                owner.getId(),
                owner.getFullName(),
                request.title(),
                totalMinor,
                ownerShareMinor,
                expiresAt
        );
        split.setParticipants(participantEntities);
        split.setCurrency(request.currency() != null ? request.currency() : "LKR");

        split = splitStore.save(split);
        log.info("Created bill split id={} ownerUserId={} totalMinor={} participants={}",
                split.getId(), owner.getId(), totalMinor, participantEntities.size());

        return SplitResponse.from(split, owner.getId());
    }

    /**
     * Get a split by ID with object-level authorization (owner or participant only).
     */
    @Transactional
    public SplitResponse getSplit(String id, Long authenticatedUserId) {
        BillSplit split = splitStore.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SPLIT_NOT_FOUND", "Bill split not found: " + id));

        checkObjectLevelAccess(split, authenticatedUserId);

        split.checkAndUpdateStatus();
        split = splitStore.save(split);

        return SplitResponse.from(split, authenticatedUserId);
    }

    /**
     * List all splits where the user is either the owner or a participant.
     */
    public List<SplitResponse> listUserSplits(Long authenticatedUserId) {
        List<BillSplit> userSplits = splitStore.findByUserId(authenticatedUserId);
        return userSplits.stream()
                .peek(BillSplit::checkAndUpdateStatus)
                .map(s -> SplitResponse.from(s, authenticatedUserId))
                .toList();
    }

    /**
     * Owner cancels unpaid shares / cancels split.
     */
    @Transactional
    public SplitResponse cancelSplit(String id, Long authenticatedUserId) {
        BillSplit split = splitStore.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SPLIT_NOT_FOUND", "Bill split not found: " + id));

        if (!split.getOwnerUserId().equals(authenticatedUserId)) {
            throw new AuthorizationException("FORBIDDEN", "Only the split owner can cancel the split");
        }

        if (split.getStatus() == SplitStatus.COMPLETED) {
            throw new ConflictException("CANNOT_CANCEL_COMPLETED", "Completed split cannot be cancelled");
        }

        split.setStatus(SplitStatus.CANCELLED);
        if (split.getParticipants() != null) {
            for (var p : split.getParticipants()) {
                if (p.getStatus() == ParticipantStatus.INVITED) {
                    p.setStatus(ParticipantStatus.CANCELLED);
                }
            }
        }
        split.setUpdatedAt(Instant.now());
        split = splitStore.save(split);

        log.info("Split id={} cancelled by owner={}", id, authenticatedUserId);
        return SplitResponse.from(split, authenticatedUserId);
    }

    /**
     * Participant declines their split share.
     */
    @Transactional
    public SplitResponse declineShare(String id, Long authenticatedUserId) {
        BillSplit split = splitStore.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SPLIT_NOT_FOUND", "Bill split not found: " + id));

        SplitParticipant participant = split.getParticipants().stream()
                .filter(p -> p.getUserId() != null && p.getUserId().equals(authenticatedUserId))
                .findFirst()
                .orElseThrow(() -> new AuthorizationException("FORBIDDEN", "You are not a participant in this split"));

        if (participant.getStatus() != ParticipantStatus.INVITED) {
            throw new ValidationException("CANNOT_DECLINE", "Participant is not in invited status: " + participant.getStatus());
        }

        participant.setStatus(ParticipantStatus.DECLINED);
        split.setUpdatedAt(Instant.now());
        split = splitStore.save(split);

        // Notify owner
        userStore.findUserById(split.getOwnerUserId()).ifPresent(ownerAcc -> {
            dispatchPrivacySafeNotification(
                    ownerAcc.toUser(),
                    "Split Declined",
                    (participant.getDisplayName() != null ? participant.getDisplayName() : "A participant") +
                            " declined their share of the bill split."
            );
        });

        log.info("Participant userId={} declined share in split id={}", authenticatedUserId, id);
        return SplitResponse.from(split, authenticatedUserId);
    }

    /**
     * Owner sends a reminder to an unpaid participant (rate limited to 1 per 24 hours per participant).
     */
    @Transactional
    public SplitResponse remindParticipant(String id, Long participantUserId, Long authenticatedUserId) {
        BillSplit split = splitStore.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SPLIT_NOT_FOUND", "Bill split not found: " + id));

        if (!split.getOwnerUserId().equals(authenticatedUserId)) {
            throw new AuthorizationException("FORBIDDEN", "Only the split owner can send reminders");
        }

        if (split.getStatus() != SplitStatus.ACTIVE) {
            throw new ValidationException("SPLIT_NOT_ACTIVE", "Cannot send reminders for inactive split: " + split.getStatus());
        }

        SplitParticipant participant = split.getParticipants().stream()
                .filter(p -> p.getUserId() != null && p.getUserId().equals(participantUserId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("PARTICIPANT_NOT_FOUND", "Participant not found with id: " + participantUserId));

        if (participant.getStatus() != ParticipantStatus.INVITED) {
            throw new ValidationException("CANNOT_REMIND", "Can only remind invited participants, current status: " + participant.getStatus());
        }

        // 24-hour rate limit check
        if (participant.getLastRemindedAt() != null) {
            long hoursSinceLastReminder = Duration.between(participant.getLastRemindedAt(), Instant.now()).toHours();
            if (hoursSinceLastReminder < 24) {
                throw new ValidationException("REMINDER_RATE_LIMITED",
                        "A reminder was already sent to this participant in the last 24 hours. Please wait before reminding again.");
            }
        }

        participant.setLastRemindedAt(Instant.now());
        split.setUpdatedAt(Instant.now());
        split = splitStore.save(split);

        // Dispatch privacy-safe reminder
        userStore.findUserById(participantUserId).ifPresent(pAcc -> {
            dispatchPrivacySafeNotification(
                    pAcc.toUser(),
                    "Bill Split Reminder",
                    "Reminder: You have a pending bill split payment. Open app to review."
            );
        });

        log.info("Sent reminder for split id={} to participant userId={}", id, participantUserId);
        return SplitResponse.from(split, authenticatedUserId);
    }

    /**
     * Participant pays their share of the split through the normal transfer flow.
     * Prevents double payment and updates split status to COMPLETED once all participants pay.
     */
    @Transactional
    public SplitResponse payShare(String id, Long authenticatedUserId, PaySplitRequest payRequest) {
        BillSplit split = splitStore.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SPLIT_NOT_FOUND", "Bill split not found: " + id));

        if (split.isExpired() || split.getStatus() == SplitStatus.EXPIRED) {
            split.setStatus(SplitStatus.EXPIRED);
            splitStore.save(split);
            throw new ValidationException("SPLIT_EXPIRED", "This bill split has expired");
        }

        if (split.getStatus() == SplitStatus.CANCELLED) {
            throw new ValidationException("SPLIT_CANCELLED", "This bill split has been cancelled");
        }

        SplitParticipant participant = split.getParticipants().stream()
                .filter(p -> p.getUserId() != null && p.getUserId().equals(authenticatedUserId))
                .findFirst()
                .orElseThrow(() -> new AuthorizationException("FORBIDDEN", "You are not a participant in this split"));

        if (participant.getStatus() == ParticipantStatus.PAID) {
            throw new ConflictException("ALREADY_PAID", "You have already paid your share for this split");
        }

        if (participant.getStatus() != ParticipantStatus.INVITED) {
            throw new ValidationException("INVALID_STATUS_FOR_PAYMENT", "Cannot pay share in status: " + participant.getStatus());
        }

        // Execute transfer from participant to split owner
        BigDecimal amount = BigDecimal.valueOf(participant.getShareMinor(), 2);
        String idempotencyKey = (payRequest != null && payRequest.idempotencyKey() != null && !payRequest.idempotencyKey().isBlank())
                ? payRequest.idempotencyKey()
                : "SPLIT-" + id + "-" + authenticatedUserId;

        InitiateTransferRequest transferReq = new InitiateTransferRequest(
                split.getOwnerUserId(),
                null,
                amount,
                split.getCurrency(),
                null,
                "Split: " + split.getTitle(),
                payRequest != null ? payRequest.pin() : null,
                payRequest != null ? payRequest.fundingSource() : null
        );

        User payer = userStore.findUserById(authenticatedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Payer not found: " + authenticatedUserId))
                .toUser();

        TransferResponse transferResp = transferService.initiateTransfer(
                transferReq,
                payer,
                payRequest != null ? payRequest.pin() : null,
                idempotencyKey,
                "127.0.0.1"
        );

        // Update participant to PAID upon successful transfer
        participant.setStatus(ParticipantStatus.PAID);
        participant.setTransferId(transferResp.transferId());
        participant.setPaidAt(Instant.now());

        // Consume payment request if present
        if (qrStore != null && participant.getPaymentRequestReference() != null) {
            try {
                qrStore.consumePaymentRequest(participant.getPaymentRequestReference(), transferResp.transferId());
            } catch (Exception ex) {
                log.debug("Payment request consume notice: {}", ex.getMessage());
            }
        }

        // Check if all participants are paid
        split.checkAndUpdateStatus();
        split.setUpdatedAt(Instant.now());
        final BillSplit savedSplit = splitStore.save(split);

        // Notify owner
        userStore.findUserById(savedSplit.getOwnerUserId()).ifPresent(ownerAcc -> {
            String pName = participant.getDisplayName() != null ? participant.getDisplayName() : "A participant";
            dispatchPrivacySafeNotification(
                    ownerAcc.toUser(),
                    "Split Share Paid",
                    pName + " has paid their share of the bill split."
            );

            if (savedSplit.getStatus() == SplitStatus.COMPLETED) {
                dispatchPrivacySafeNotification(
                        ownerAcc.toUser(),
                        "Bill Split Completed",
                        "All participants have settled their shares for the bill split."
                );
            }
        });

        log.info("Participant userId={} paid share of {} minor in split id={}, transferId={}",
                authenticatedUserId, participant.getShareMinor(), id, transferResp.transferId());

        return SplitResponse.from(savedSplit, authenticatedUserId);
    }

    private User resolveUser(SplitParticipantRequest pReq) {
        if (pReq.userId() != null) {
            return userStore.findUserById(pReq.userId())
                    .orElseThrow(() -> new ValidationException("PARTICIPANT_NOT_FOUND", "Participant not registered with userId: " + pReq.userId()))
                    .toUser();
        } else if (pReq.phone() != null && !pReq.phone().isBlank()) {
            String normalizedPhone;
            try {
                normalizedPhone = PhoneNumberNormalizer.normalize(pReq.phone());
            } catch (Exception e) {
                normalizedPhone = pReq.phone().trim();
            }
            return userStore.findUserByPhone(normalizedPhone)
                    .orElseThrow(() -> new ValidationException("PARTICIPANT_NOT_FOUND", "Participant not registered with phone: " + pReq.phone()))
                    .toUser();
        } else {
            throw new ValidationException("PARTICIPANT_IDENTITY_REQUIRED", "Either userId or phone must be provided for each participant");
        }
    }

    private void checkObjectLevelAccess(BillSplit split, Long authenticatedUserId) {
        boolean isOwner = split.getOwnerUserId().equals(authenticatedUserId);
        boolean isParticipant = split.getParticipants() != null && split.getParticipants().stream()
                .anyMatch(p -> p.getUserId() != null && p.getUserId().equals(authenticatedUserId));

        if (!isOwner && !isParticipant) {
            throw new AuthorizationException("FORBIDDEN", "You do not have authorization to access this bill split");
        }
    }

    private void dispatchPrivacySafeNotification(User user, String title, String message) {
        if (notificationService == null || user == null) return;
        try {
            notificationService.dispatch(user, NotificationType.GENERAL, title, message);
        } catch (Exception ex) {
            log.warn("Notification dispatch failed for userId={}: {}", user.getId(), ex.getMessage());
        }
    }
}
