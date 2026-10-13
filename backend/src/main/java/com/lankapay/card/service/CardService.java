package com.lankapay.card.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.card.domain.Card;
import com.lankapay.card.domain.CardKind;
import com.lankapay.card.domain.CardStatus;
import com.lankapay.card.domain.CardStore;
import com.lankapay.card.web.dto.*;
import com.lankapay.common.exception.*;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.virtualcard.provider.CardProvider;
import com.lankapay.virtualcard.provider.CardRevealDetails;
import com.lankapay.virtualcard.provider.CardTokenInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Service managing linked and virtual card lifecycles.
 *
 * Rules strictly enforced:
 * - PCI-DSS: NO PAN, CVV, or PIN are ever logged or persisted.
 * - Object-level authorization: users can only see/modify their own cards (other users get 404).
 * - Step-up authentication (PIN) for sensitive operations.
 * - System risk limits for card spending.
 * - Explicit user consent recorded in "consents" collection.
 * - Privacy-safe lock-screen notifications (no card/account digits).
 */
@Service
public class CardService {

    private static final Logger log = LoggerFactory.getLogger(CardService.class);

    private final CardStore                      cardStore;
    private final CardProvider                   cardProvider;
    private final LinkedAccountStore             linkedAccountStore;
    private final UserStore                      userStore;
    private final PasswordEncoder                passwordEncoder;
    private final CardRevealRateLimiter          revealRateLimiter;
    private final CardSuspiciousPatternDetector  suspiciousPatternDetector;
    private final NotificationService            notificationService;
    private final AuditService                   auditService;

    public CardService(CardStore cardStore,
                       CardProvider cardProvider,
                       LinkedAccountStore linkedAccountStore,
                       UserStore userStore,
                       PasswordEncoder passwordEncoder,
                       CardRevealRateLimiter revealRateLimiter,
                       CardSuspiciousPatternDetector suspiciousPatternDetector,
                       NotificationService notificationService,
                       AuditService auditService) {
        this.cardStore                 = cardStore;
        this.cardProvider              = cardProvider;
        this.linkedAccountStore         = linkedAccountStore;
        this.userStore                 = userStore;
        this.passwordEncoder           = passwordEncoder;
        this.revealRateLimiter         = revealRateLimiter;
        this.suspiciousPatternDetector = suspiciousPatternDetector;
        this.notificationService       = notificationService;
        this.auditService              = auditService;
    }

    // ── 1. POST /cards (Link existing card) ─────────────────────

    public Card linkCard(Long userId, CreateCardRequest request, String stepUpPinHeader, String clientIp) {
        User user = requireUser(userId);

        // 1. Explicit consent required
        if (!Boolean.TRUE.equals(request.consentGiven())) {
            throw new ValidationException("CONSENT_REQUIRED", "Explicit user consent is required to link a card");
        }

        // 2. Step-up authentication required
        String effectivePin = (request.pin() != null && !request.pin().isBlank()) ? request.pin() : stepUpPinHeader;
        verifyStepUpPin(userId, effectivePin);

        // 3. Validate token with CardProvider (verifies token exists and belongs to this user)
        CardTokenInfo tokenInfo = cardProvider.getTokenInfo(request.cardToken(), userId);

        // 4. Validate linked bank account if supplied
        if (request.linkedBankAccountId() != null) {
            LinkedAccount account = linkedAccountStore.findAccountByIdAndUserId(request.linkedBankAccountId(), userId)
                    .orElseThrow(() -> new ValidationException("LINKED_ACCOUNT_NOT_FOUND",
                            "Linked bank account not found or does not belong to this user"));
            if (!account.isActive()) {
                throw new ValidationException("LINKED_ACCOUNT_NOT_ACTIVE", "Linked bank account is not active");
            }
        }

        // 5. Start ownership challenge
        String challengeRef = cardProvider.startOwnershipChallenge(request.cardToken(), userId, clientIp);

        // 6. Create card in PENDING_VERIFICATION
        Card card = new Card(
                userId,
                request.linkedBankAccountId(),
                request.cardToken(),
                tokenInfo.last4(),
                parseMonth(tokenInfo.expMonth()),
                parseYear(tokenInfo.expYear()),
                tokenInfo.brandLabel() != null ? tokenInfo.brandLabel() : "TEST CARD",
                request.nickname(),
                CardKind.LINKED,
                CardStatus.PENDING_VERIFICATION,
                challengeRef
        );

        Card savedCard = cardStore.save(card);

        // 7. Record explicit consent
        cardStore.saveConsent(userId, savedCard.id(), "LINK_CARD", true, clientIp);

        // 8. Audit & Notification
        auditService.log(userId, "CARD_ADDED", "CARD", savedCard.id(),
                "{\"last4\":\"" + tokenInfo.last4() + "\",\"cardKind\":\"LINKED\"}", clientIp);
        notificationService.notifyCardAdded(user);

        log.info("Card added in PENDING_VERIFICATION userId={} cardId={} last4={}", userId, savedCard.id(), tokenInfo.last4());
        return savedCard;
    }

    // ── 2. POST /cards/{id}/verify (OTP verification) ──────────

    public Card verifyCard(Long cardId, Long userId, String otp, String clientIp) {
        User user = requireUser(userId);
        Card card = requireOwnedCard(cardId, userId);

        if (card.status() != CardStatus.PENDING_VERIFICATION) {
            throw new ValidationException("CARD_NOT_PENDING_VERIFICATION",
                    "Card is not awaiting verification (current status: " + card.status() + ")");
        }

        try {
            cardProvider.confirmChallenge(card.challengeRef(), userId, otp);
        } catch (OtpException ex) {
            suspiciousPatternDetector.recordFailedVerification(user, cardId, clientIp);
            throw ex;
        }

        Card verifiedCard = card.withStatus(CardStatus.ACTIVE);
        Card saved = cardStore.save(verifiedCard);

        auditService.log(userId, "CARD_VERIFIED", "CARD", cardId,
                "{\"last4\":\"" + card.last4() + "\"}", clientIp);
        notificationService.notifyCardVerified(user);

        log.info("Card verified to ACTIVE userId={} cardId={}", userId, cardId);
        return saved;
    }

    // ── 3. GET /cards & GET /cards/{id} ────────────────────────

    public List<Card> listCards(Long userId) {
        requireUser(userId);
        return cardStore.findAllByUserId(userId);
    }

    public Card getCard(Long cardId, Long userId) {
        requireUser(userId);
        return requireOwnedCard(cardId, userId);
    }

    // ── 4. PATCH /cards/{id} (Update details & limits) ─────────

    public Card patchCard(Long cardId, Long userId, PatchCardRequest request, String stepUpPinHeader, String clientIp) {
        User user = requireUser(userId);
        Card card = requireOwnedCard(cardId, userId);

        if (card.isRemoved() || card.isExpired()) {
            throw new ValidationException("CARD_INACTIVE",
                    "Cannot modify limits or settings on a " + card.status() + " card");
        }

        // Validate risk limits
        if (request.perPaymentLimitMinor() != null) {
            if (request.perPaymentLimitMinor() > Card.MAX_PER_PAYMENT_LIMIT_MINOR) {
                throw new ValidationException("RISK_LIMIT_EXCEEDED",
                        "Per-payment limit exceeds system maximum allowed risk limit of "
                                + (Card.MAX_PER_PAYMENT_LIMIT_MINOR / 100) + " LKR");
            }
        }
        if (request.dailyLimitMinor() != null) {
            if (request.dailyLimitMinor() > Card.MAX_DAILY_LIMIT_MINOR) {
                throw new ValidationException("RISK_LIMIT_EXCEEDED",
                        "Daily limit exceeds system maximum allowed risk limit of "
                                + (Card.MAX_DAILY_LIMIT_MINOR / 100) + " LKR");
            }
        }

        long effectivePerLimit   = request.perPaymentLimitMinor() != null ? request.perPaymentLimitMinor() : card.perPaymentLimitMinor();
        long effectiveDailyLimit = request.dailyLimitMinor() != null ? request.dailyLimitMinor() : card.dailyLimitMinor();

        if (effectivePerLimit > effectiveDailyLimit) {
            throw new ValidationException("INVALID_LIMITS", "Per-payment limit cannot exceed the aggregate daily limit");
        }

        // Limit change rules: lowering is allowed without step-up; raising requires step-up auth
        boolean raisingPer   = request.perPaymentLimitMinor() != null && request.perPaymentLimitMinor() > card.perPaymentLimitMinor();
        boolean raisingDaily = request.dailyLimitMinor() != null && request.dailyLimitMinor() > card.dailyLimitMinor();

        if (raisingPer || raisingDaily) {
            String effectivePin = (request.pin() != null && !request.pin().isBlank()) ? request.pin() : stepUpPinHeader;
            verifyStepUpPin(userId, effectivePin);
        }

        Card updated = card.withDetails(
                request.nickname(),
                request.perPaymentLimitMinor(),
                request.dailyLimitMinor(),
                request.onlinePaymentsEnabled()
        );
        Card saved = cardStore.save(updated);

        boolean limitsChanged = (request.perPaymentLimitMinor() != null && !request.perPaymentLimitMinor().equals(card.perPaymentLimitMinor()))
                || (request.dailyLimitMinor() != null && !request.dailyLimitMinor().equals(card.dailyLimitMinor()));

        if (limitsChanged) {
            notificationService.notifyCardLimitChanged(user);
        }

        auditService.log(userId, "CARD_UPDATED", "CARD", cardId,
                "{\"limitsChanged\":" + limitsChanged + ",\"onlineEnabled\":" + saved.onlinePaymentsEnabled() + "}",
                clientIp);

        return saved;
    }

    // ── 5. POST /cards/{id}/freeze & /unfreeze ─────────────────

    public Card freezeCard(Long cardId, Long userId, String clientIp) {
        User user = requireUser(userId);
        Card card = requireOwnedCard(cardId, userId);

        if (card.status() != CardStatus.ACTIVE) {
            throw new ValidationException("CARD_NOT_ACTIVE", "Only active cards can be frozen (current status: " + card.status() + ")");
        }

        Card frozen = card.withStatus(CardStatus.FROZEN);
        Card saved  = cardStore.save(frozen);

        auditService.log(userId, "CARD_FROZEN", "CARD", cardId, null, clientIp);
        notificationService.notifyCardFrozen(user);

        log.info("Card frozen userId={} cardId={}", userId, cardId);
        return saved;
    }

    public Card unfreezeCard(Long cardId, Long userId, String pin, String stepUpPinHeader, String clientIp) {
        User user = requireUser(userId);
        Card card = requireOwnedCard(cardId, userId);

        if (card.status() != CardStatus.FROZEN) {
            throw new ValidationException("CARD_NOT_FROZEN", "Only frozen cards can be unfrozen (current status: " + card.status() + ")");
        }

        String effectivePin = (pin != null && !pin.isBlank()) ? pin : stepUpPinHeader;
        verifyStepUpPin(userId, effectivePin);

        Card active = card.withStatus(CardStatus.ACTIVE);
        Card saved  = cardStore.save(active);

        auditService.log(userId, "CARD_UNFROZEN", "CARD", cardId, null, clientIp);
        notificationService.notifyCardUnfrozen(user);

        log.info("Card unfrozen to ACTIVE userId={} cardId={}", userId, cardId);
        return saved;
    }

    // ── 6. POST /cards/{id}/reveal (Virtual cards only) ────────

    public CardRevealResponse revealCard(Long cardId, Long userId, String pin, String stepUpPinHeader, String clientIp) {
        User user = requireUser(userId);
        Card card = requireOwnedCard(cardId, userId);

        // 1. Step-up auth required
        String effectivePin = (pin != null && !pin.isBlank()) ? pin : stepUpPinHeader;
        verifyStepUpPin(userId, effectivePin);

        // 2. Rate limit check
        revealRateLimiter.checkLimit(userId);

        // 3. Refuse linked cards — supported only for virtual cards
        if (card.cardKind() != CardKind.VIRTUAL) {
            throw new ValidationException("REVEAL_NOT_SUPPORTED",
                    "Card details reveal is permitted only for platform-issued virtual cards. Linked physical cards cannot be revealed.");
        }

        // 4. Retrieve reveal details from simulator
        CardRevealDetails details = cardProvider.revealVirtualCard(card.cardToken(), userId);

        // 5. Track for suspicious reveal frequency
        suspiciousPatternDetector.recordReveal(user, cardId, clientIp);

        // 6. Notifications & Audit (NEVER LOG PAN OR CVV)
        notificationService.notifyCardDetailsViewed(user);
        auditService.log(userId, "CARD_DETAILS_VIEWED", "CARD", cardId, null, clientIp);

        // Notice: do NOT log details.pan() or details.cvv()
        log.info("Card details revealed userId={} cardId={}", userId, cardId);

        return new CardRevealResponse(
                card.id(),
                card.last4(),
                details.pan(),
                details.cvv(),
                details.expMonth(),
                details.expYear(),
                details.brandLabel(),
                details.expiresInSeconds(),
                true
        );
    }

    // ── 7. POST /cards/virtual (Issue new virtual card) ────────

    public Card issueVirtualCard(Long userId, VirtualCardIssueRequest request, String stepUpPinHeader, String clientIp) {
        User user = requireUser(userId);

        // 1. Step-up authentication required
        String effectivePin = (request.pin() != null && !request.pin().isBlank()) ? request.pin() : stepUpPinHeader;
        verifyStepUpPin(userId, effectivePin);

        // 2. Backing linked bank account must be owned and active
        LinkedAccount account = linkedAccountStore.findAccountByIdAndUserId(request.linkedBankAccountId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("BANK_ACCOUNT_NOT_FOUND",
                        "Linked bank account not found or does not belong to this user"));
        if (!account.isActive()) {
            throw new ValidationException("ACCOUNT_NOT_ACTIVE", "Linked bank account is not active");
        }

        // 3. Issue virtual card via provider
        CardTokenInfo tokenInfo = cardProvider.issueVirtualCard(userId);

        // 4. Persist as ACTIVE
        Card card = new Card(
                userId,
                request.linkedBankAccountId(),
                tokenInfo.cardToken(),
                tokenInfo.last4(),
                parseMonth(tokenInfo.expMonth()),
                parseYear(tokenInfo.expYear()),
                "TEST CARD",
                request.nickname() != null ? request.nickname() : "Virtual Card",
                CardKind.VIRTUAL,
                CardStatus.ACTIVE,
                null
        );
        Card saved = cardStore.save(card);

        // 5. Record consent
        cardStore.saveConsent(userId, saved.id(), "ISSUE_VIRTUAL_CARD", true, clientIp);

        // 6. Audit & Notification
        auditService.log(userId, "CARD_VIRTUAL_ISSUED", "CARD", saved.id(),
                "{\"last4\":\"" + tokenInfo.last4() + "\"}", clientIp);
        notificationService.notifyCardAdded(user);

        log.info("Virtual card issued userId={} cardId={} last4={}", userId, saved.id(), tokenInfo.last4());
        return saved;
    }

    // ── 8. DELETE /cards/{id} (Remove card) ────────────────────

    public void removeCard(Long cardId, Long userId, String clientIp) {
        removeCard(cardId, userId, null, clientIp);
    }

    public void removeCard(Long cardId, Long userId, String stepUpPinHeader, String clientIp) {
        User user = requireUser(userId);
        Card card = requireOwnedCard(cardId, userId);

        if (card.status() == CardStatus.REMOVED) {
            throw new ConflictException("CARD_ALREADY_REMOVED", "This card is already removed");
        }

        // If step-up PIN is supplied, verify it
        if (stepUpPinHeader != null && !stepUpPinHeader.isBlank()) {
            verifyStepUpPin(userId, stepUpPinHeader);
        }

        Card removed = card.withStatus(CardStatus.REMOVED);
        cardStore.save(removed);

        cardStore.saveConsent(userId, cardId, "REMOVE_CARD", true, clientIp);
        auditService.log(userId, "CARD_REMOVED", "CARD", cardId, null, clientIp);
        notificationService.notifyCardRemoved(user);

        log.info("Card removed userId={} cardId={}", userId, cardId);
    }

    // ── 9. Expired-card processing ─────────────────────────────

    public int expireOutdatedCards(LocalDate asOf) {
        LocalDate date = (asOf != null) ? asOf : LocalDate.now();
        List<Card> expiredCandidates = cardStore.findCardsToProcessExpiration(date.getYear(), date.getMonthValue());

        int count = 0;
        for (Card card : expiredCandidates) {
            Card expired = card.withStatus(CardStatus.EXPIRED);
            cardStore.save(expired);
            auditService.log(card.userId(), "CARD_EXPIRED", "CARD", card.id(), null, "SYSTEM");
            count++;
        }

        if (count > 0) {
            log.info("Expired-card job processed {} expired cards as of {}", count, date);
        }
        return count;
    }

    // ── Internal Helpers ───────────────────────────────────────

    private User requireUser(Long userId) {
        UserAccount account = userStore.findUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER", userId));
        User u = new User(account.phone(), account.fullName(), account.nicNumber(), account.passwordHash());
        u.setId(account.id());
        u.setStatus(account.status());
        u.setRole(account.role());
        return u;
    }

    /**
     * Enforce object-level authorization.
     * Returns 404 (ResourceNotFoundException) for non-existent IDs and cards owned by another user.
     */
    private Card requireOwnedCard(Long cardId, Long userId) {
        return cardStore.findByIdAndUserId(cardId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("CARD_NOT_FOUND",
                        "Card with id " + cardId + " not found"));
    }

    private void verifyStepUpPin(Long userId, String pin) {
        if (pin == null || pin.isBlank()) {
            throw new AuthorizationException("STEP_UP_AUTH_REQUIRED",
                    "Step-up authentication required: PIN must be provided");
        }
        UserAccount account = userStore.findUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER", userId));
        if (!passwordEncoder.matches(pin, account.passwordHash())) {
            throw new InvalidCredentialsException("INVALID_PIN",
                    "Invalid PIN provided for step-up authentication");
        }
    }

    private static Integer parseMonth(String mm) {
        if (mm == null || mm.isBlank()) return null;
        try {
            return Integer.parseInt(mm.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseYear(String yy) {
        if (yy == null || yy.isBlank()) return null;
        try {
            int y = Integer.parseInt(yy.trim());
            return y < 100 ? 2000 + y : y;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
