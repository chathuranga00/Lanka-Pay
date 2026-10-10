package com.lankapay.transfer.service;

import com.lankapay.common.exception.UnprocessableEntityException;
import com.lankapay.identity.domain.User;
import com.lankapay.transfer.domain.DailyLimit;
import com.lankapay.transfer.repository.DailyLimitRepository;
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

    private final DailyLimitRepository dailyLimitRepository;

    public DailyLimitService(DailyLimitRepository dailyLimitRepository) {
        this.dailyLimitRepository = dailyLimitRepository;
    }

    /**
     * Verify that the user has sufficient remaining daily limit for the proposed amount.
     *
     * @throws UnprocessableEntityException if the daily limit would be exceeded
     */
    @Transactional(readOnly = true)
    public void checkLimit(User user, BigDecimal amount) {
        LocalDate today = LocalDate.now();
        DailyLimit limit = dailyLimitRepository.findByUserIdAndLimitDate(user.getId(), today)
                .orElse(null);

        BigDecimal currentTotal = limit != null ? limit.getTotalSent() : BigDecimal.ZERO;
        BigDecimal dailyCap = limit != null ? limit.getDailyCap() : DEFAULT_DAILY_CAP;
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
        DailyLimit limit = dailyLimitRepository.findByUserIdAndLimitDate(user.getId(), today)
                .orElseGet(() -> new DailyLimit(user, today, DEFAULT_DAILY_CAP));

        limit.addSent(amount);
        dailyLimitRepository.save(limit);
        log.info("Recorded daily spend of {} LKR for user id={}, new total={}",
                amount, user.getId(), limit.getTotalSent());
    }
}
