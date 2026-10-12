package com.lankapay.transfer.service;

import com.lankapay.card.domain.Card;
import com.lankapay.common.exception.UnprocessableEntityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fraud rules for card-funded transfers per Section 8:
 * - Velocity checks per card (max 5 requests per 60 seconds).
 * - New-card limit for the first 24 hours (25,000.00 LKR max).
 * - Alert on repeated declines (3 consecutive declines blocks card).
 */
@Service
public class CardTransferFraudService {

    private static final Logger log = LoggerFactory.getLogger(CardTransferFraudService.class);

    public static final long NEW_CARD_24H_LIMIT_MINOR = 2_500_000L; // 25,000.00 LKR
    public static final int MAX_VELOCITY_PER_MINUTE   = 5;
    public static final int MAX_CONSECUTIVE_DECLINES   = 3;

    private final ConcurrentHashMap<Long, Deque<Instant>> velocityMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicInteger> declineCounts = new ConcurrentHashMap<>();

    /**
     * Evaluate fraud checks before processing a card transfer.
     */
    public void evaluatePreFlight(Card card, BigDecimal amount) {
        // 1. Repeated declines check
        AtomicInteger declines = declineCounts.get(card.id());
        if (declines != null && declines.get() >= MAX_CONSECUTIVE_DECLINES) {
            log.warn("[FRAUD ALERT] Card id={} is blocked due to {} repeated declines",
                    card.id(), declines.get());
            throw new UnprocessableEntityException("CARD_BLOCKED",
                    "Card is temporarily blocked due to repeated declines");
        }

        // 2. Velocity check per card
        Instant now = Instant.now();
        Instant windowStart = now.minus(60, ChronoUnit.SECONDS);
        Deque<Instant> timestamps = velocityMap.computeIfAbsent(card.id(), k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_VELOCITY_PER_MINUTE) {
                log.warn("[FRAUD ALERT] Velocity check triggered for card id={}: {} payments in 60s",
                        card.id(), timestamps.size());
                throw new UnprocessableEntityException("LIMIT_EXCEEDED",
                        "Card transaction velocity limit exceeded. Please wait a moment.");
            }
            timestamps.addLast(now);
        }

        // 3. New-card limit for the first 24 hours
        Instant referenceTime = card.verifiedAt() != null ? card.verifiedAt() : card.createdAt();
        if (referenceTime != null && referenceTime.isAfter(now.minus(24, ChronoUnit.HOURS))) {
            long amountMinor = amount.movePointRight(2).longValue();
            if (amountMinor > NEW_CARD_24H_LIMIT_MINOR) {
                log.warn("[FRAUD ALERT] New-card 24-hour limit exceeded for card id={}: amount={}",
                        card.id(), amount);
                throw new UnprocessableEntityException("LIMIT_EXCEEDED",
                        "New-card 24-hour limit exceeded. Maximum transfer for cards linked within 24 hours is %s LKR"
                                .formatted(BigDecimal.valueOf(NEW_CARD_24H_LIMIT_MINOR, 2)));
            }
        }
    }

    /**
     * Record a decline for the card and trigger an alert if threshold reached.
     */
    public void recordDecline(Long cardId, String reason) {
        if (cardId == null) return;
        AtomicInteger count = declineCounts.computeIfAbsent(cardId, k -> new AtomicInteger(0));
        int total = count.incrementAndGet();
        log.warn("Card id={} declined (total consecutive declines={}): reason='{}'", cardId, total, reason);
        if (total >= MAX_CONSECUTIVE_DECLINES) {
            log.error("[FRAUD ALERT] Repeated declines on card id={} reached threshold ({})! Card flagged.",
                    cardId, total);
        }
    }

    /**
     * Reset decline count on successful payment.
     */
    public void recordSuccess(Long cardId) {
        if (cardId == null) return;
        declineCounts.remove(cardId);
    }
}
