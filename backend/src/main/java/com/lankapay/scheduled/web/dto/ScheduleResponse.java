package com.lankapay.scheduled.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lankapay.scheduled.domain.*;
import com.lankapay.transfer.domain.FundingSource;

import java.time.Instant;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScheduleResponse(
        Long id,
        Long userId,
        ScheduleType type,
        ScheduledPayee payee,
        Long amountMinor,
        String currency,
        boolean isBillAmount,
        FundingSource fundingSource,
        ScheduleFrequency frequency,
        LocalDate startDate,
        LocalDate endDate,
        Instant nextRunAt,
        ScheduleStatus status,
        int consecutiveFailures,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static ScheduleResponse from(ScheduledPayment s) {
        return new ScheduleResponse(
                s.id(),
                s.userId(),
                s.type(),
                s.payee(),
                s.amountMinor(),
                s.currency(),
                s.isBillAmount(),
                s.fundingSource(),
                s.frequency(),
                s.startDate(),
                s.endDate(),
                s.nextRunAt(),
                s.status(),
                s.consecutiveFailures(),
                s.version(),
                s.createdAt(),
                s.updatedAt()
        );
    }
}
