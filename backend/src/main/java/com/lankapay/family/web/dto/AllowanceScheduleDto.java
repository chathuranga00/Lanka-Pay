package com.lankapay.family.web.dto;

import com.lankapay.scheduled.domain.ScheduleFrequency;

import java.time.LocalDate;

public record AllowanceScheduleDto(
        boolean enabled,
        Long amountMinor,
        ScheduleFrequency frequency,
        LocalDate nextRunDate,
        String sourceAccount
) {}
