package com.lankapay.transfer.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Plain Java domain record for daily transfer limit tracking (no JPA annotations).
 */
public record DailyLimitRecord(
        Long id,
        Long userId,
        LocalDate limitDate,
        BigDecimal totalSent,
        BigDecimal dailyCap
) {
    public boolean hasCapacity(BigDecimal amount) {
        BigDecimal cap = dailyCap != null ? dailyCap : new BigDecimal("100000.00");
        BigDecimal sent = totalSent != null ? totalSent : BigDecimal.ZERO;
        return sent.add(amount).compareTo(cap) <= 0;
    }
}
