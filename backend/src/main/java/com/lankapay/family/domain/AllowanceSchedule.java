package com.lankapay.family.domain;

import com.lankapay.scheduled.domain.ScheduleFrequency;

import java.time.LocalDate;

/**
 * Reusable allowance schedule configuration (leveraging scheduled payment concepts).
 */
public record AllowanceSchedule(
        boolean enabled,
        Long amountMinor,
        ScheduleFrequency frequency,
        LocalDate nextRunDate,
        String sourceAccount
) {
    public static AllowanceSchedule disabled() {
        return new AllowanceSchedule(false, 0L, ScheduleFrequency.WEEKLY, null, null);
    }
}
