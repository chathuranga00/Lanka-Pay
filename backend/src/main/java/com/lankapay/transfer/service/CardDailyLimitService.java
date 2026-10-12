package com.lankapay.transfer.service;

import com.lankapay.card.domain.Card;
import com.lankapay.common.exception.UnprocessableEntityException;
import com.lankapay.transfer.domain.TransactionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages daily limits for card-funded transfers.
 *
 * Enforces:
 * - Card per-payment limit
 * - Card daily limit (counted from confirmed and pending card payments)
 * - Global risk limits
 * - Thread-safe atomic reservation to ensure that concurrent payments
 *   with one card that would exceed the daily limit properly reject the excess.
 */
@Service
public class CardDailyLimitService {

    private static final Logger log = LoggerFactory.getLogger(CardDailyLimitService.class);

    private final TransactionStore transactionStore;
    private final ConcurrentHashMap<String, AtomicLong> activeDailySpends = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Object> cardLocks = new ConcurrentHashMap<>();

    public CardDailyLimitService(TransactionStore transactionStore) {
        this.transactionStore = transactionStore;
    }

    private Object getLock(Long cardId) {
        return cardLocks.computeIfAbsent(cardId, k -> new Object());
    }

    private String key(Long cardId, LocalDate date) {
        return cardId + "_" + date;
    }

    /**
     * Get already spent/reserved minor units for this card today.
     */
    public long getSpentTodayMinor(Long cardId, LocalDate date) {
        String k = key(cardId, date);
        return activeDailySpends.computeIfAbsent(k, key -> {
            long fromStore = transactionStore.getCardDailySpentMinor(cardId, date);
            return new AtomicLong(fromStore);
        }).get();
    }

    /**
     * Remaining daily limit in minor units.
     */
    public long getRemainingDailyLimitMinor(Card card, LocalDate date) {
        long spent = getSpentTodayMinor(card.id(), date);
        return Math.max(0L, card.dailyLimitMinor() - spent);
    }

    /**
     * Validate proposed payment against card and global limits without reserving.
     */
    public void validateLimit(Card card, BigDecimal amount) {
        long amountMinor = amount.movePointRight(2).longValue();

        if (amountMinor > card.perPaymentLimitMinor()) {
            throw new UnprocessableEntityException("LIMIT_EXCEEDED",
                    "Transfer amount exceeds card per-payment limit of %s LKR"
                            .formatted(BigDecimal.valueOf(card.perPaymentLimitMinor(), 2)));
        }

        if (amountMinor > Card.MAX_PER_PAYMENT_LIMIT_MINOR) {
            throw new UnprocessableEntityException("LIMIT_EXCEEDED",
                    "Transfer amount exceeds maximum system limit of %s LKR"
                            .formatted(BigDecimal.valueOf(Card.MAX_PER_PAYMENT_LIMIT_MINOR, 2)));
        }

        LocalDate today = LocalDate.now();
        long spent = getSpentTodayMinor(card.id(), today);
        long projected = spent + amountMinor;

        if (projected > card.dailyLimitMinor()) {
            long remaining = Math.max(0L, card.dailyLimitMinor() - spent);
            throw new UnprocessableEntityException("LIMIT_EXCEEDED",
                    "Transfer amount exceeds remaining card daily limit of %s LKR"
                            .formatted(BigDecimal.valueOf(remaining, 2)));
        }

        if (projected > Card.MAX_DAILY_LIMIT_MINOR) {
            throw new UnprocessableEntityException("LIMIT_EXCEEDED",
                    "Transfer amount exceeds maximum system daily limit of %s LKR"
                            .formatted(BigDecimal.valueOf(Card.MAX_DAILY_LIMIT_MINOR, 2)));
        }
    }

    /**
     * Atomically check and reserve the spend on the card for today.
     * Thread-safe against concurrent payments on the same card.
     */
    public void reserve(Card card, BigDecimal amount) {
        synchronized (getLock(card.id())) {
            validateLimit(card, amount);
            long amountMinor = amount.movePointRight(2).longValue();
            LocalDate today = LocalDate.now();
            String k = key(card.id(), today);
            AtomicLong total = activeDailySpends.computeIfAbsent(k, key -> {
                long fromStore = transactionStore.getCardDailySpentMinor(card.id(), today);
                return new AtomicLong(fromStore);
            });
            total.addAndGet(amountMinor);
            log.info("Reserved {} minor units for card id={}, new daily total={}",
                    amountMinor, card.id(), total.get());
        }
    }

    /**
     * Release reserved spend if payment was declined or failed.
     */
    public void release(Card card, BigDecimal amount) {
        if (card == null || amount == null) return;
        synchronized (getLock(card.id())) {
            long amountMinor = amount.movePointRight(2).longValue();
            LocalDate today = LocalDate.now();
            String k = key(card.id(), today);
            AtomicLong total = activeDailySpends.get(k);
            if (total != null) {
                total.updateAndGet(curr -> Math.max(0L, curr - amountMinor));
                log.info("Released {} minor units for card id={}, new daily total={}",
                        amountMinor, card.id(), total.get());
            }
        }
    }

    /**
     * Commit spend on confirmed capture (spend remains reserved).
     */
    public void commit(Card card, BigDecimal amount) {
        // Spend was already added during reserve(); no-op other than logging.
        log.info("Committed {} LKR spend for card id={}", amount, card.id());
    }
}
