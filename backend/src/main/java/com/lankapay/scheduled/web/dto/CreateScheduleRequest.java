package com.lankapay.scheduled.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lankapay.scheduled.domain.ScheduleFrequency;
import com.lankapay.scheduled.domain.ScheduleType;
import com.lankapay.scheduled.domain.ScheduledPayee;
import com.lankapay.transfer.domain.FundingSource;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateScheduleRequest(
        @NotNull ScheduleType type,
        @NotNull ScheduledPayee payee,
        Long amountMinor,
        String currency,
        Boolean isBillAmount,
        @NotNull FundingSource fundingSource,
        @NotNull ScheduleFrequency frequency,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        Boolean consent,
        String pin
) {}
