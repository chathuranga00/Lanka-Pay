package com.lankapay.scheduled.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lankapay.scheduled.domain.ScheduleFrequency;
import com.lankapay.scheduled.domain.ScheduleStatus;
import com.lankapay.scheduled.domain.ScheduledPayee;
import com.lankapay.transfer.domain.FundingSource;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateScheduleRequest(
        ScheduledPayee payee,
        Long amountMinor,
        Boolean isBillAmount,
        FundingSource fundingSource,
        ScheduleFrequency frequency,
        LocalDate endDate,
        ScheduleStatus status,
        String pin
) {}
