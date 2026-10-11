package com.lankapay.transfer.service;

import com.lankapay.common.exception.UnprocessableEntityException;
import com.lankapay.identity.domain.User;
import com.lankapay.transfer.domain.DailyLimitRecord;
import com.lankapay.transfer.domain.TransactionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class DailyLimitService {

    private static final Logger log = LoggerFactory.getLogger(DailyLimitService.class);
    private static final BigDecimal DEFAULT_DAILY_CAP = new BigDecimal("100000.00");

    private final TransactionStore transactionStore;

    public DailyLimitService(TransactionStore transactionStore) {
        this.transactionStore = transactionStore;
    }

    /**
     * Verify that the user has sufficient remaining daily limit for the proposed amount.
     *
     * @throws UnprocessableEntityException if the daily limit would be exceeded
     */
    @Transactional(readOnly = true)
    public void checkLimit(User user, BigDecimal amount) {
        LocalDate today = LocalDate.now();
        BigDecimal currentTotal = BigDecimal.ZERO;
        BigDecimal dailyCap = DEFAULT_DAILY_CAP;

        var limitOpt = transactionStore.findDailyLimit(user.getId(), today);
        if (limitOpt.isPresent()) {
            currentTotal = limitOpt.get().totalSent() != null ? limitOpt.get().totalSent() : BigDecimal.ZERO;
            dailyCap = limitOpt.get().dailyCap() != null ? limitOpt.get().dailyCap() : DEFAULT_DAILY_CAP;
        }

        BigDecimal projectedTotal = currentTotal.add(amount);

        if (projectedTotal.compareTo(dailyCap) > 0) {
            log.warn("Daily limit exceeded for user id={}: current={}, proposed={}, cap={}",
                    user.getId(), currentTotal, amount, dailyCap);
            throw new UnprocessableEntityException("DAILY_LIMIT_EXCEEDED",
                    "Transfer amount exceeds daily limit of %s LKR (already sent today: %s LKR)"
                            .formatted(dailyCap, currentTotal));
        }
    }

    /**
     * Atomically record the spent amount for today.
     */
    @Transactional
    public void recordSpend(User user, BigDecimal amount) {
        LocalDate today = LocalDate.now();
        var existing = transactionStore.findDailyLimit(user.getId(), today).orElse(null);
        BigDecimal currentTotal = existing != null && existing.totalSent() != null ? existing.totalSent() : BigDecimal.ZERO;
        BigDecimal cap = existing != null && existing.dailyCap() != null ? existing.dailyCap() : DEFAULT_DAILY_CAP;
        BigDecimal newTotal = currentTotal.add(amount);
        transactionStore.saveDailyLimit(new DailyLimitRecord(
                existing != null ? existing.id() : null,
                user.getId(),
                today,
                newTotal,
                cap
        ));
        log.info("Recorded daily spend of {} LKR for user id={}, new total={}",
                amount, user.getId(), newTotal);
    }
}
