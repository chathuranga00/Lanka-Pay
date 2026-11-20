package com.lankapay.scheduled.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lankapay.scheduled.domain.ScheduledRunRecord;
import com.lankapay.scheduled.domain.ScheduledRunStatus;

import java.time.Instant;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScheduleRunResponse(
        Long id,
        Long scheduleId,
        LocalDate runDate,
        String idempotencyKey,
        String transactionRef,
        Long amountMinor,
        String currency,
        ScheduledRunStatus status,
        String failureReason,
        Instant executedAt
) {
    public static ScheduleRunResponse from(ScheduledRunRecord r) {
        return new ScheduleRunResponse(
                r.id(),
                r.scheduleId(),
                r.runDate(),
                r.idempotencyKey(),
                r.transactionRef(),
                r.amountMinor(),
                r.currency(),
                r.status(),
                r.failureReason(),
                r.executedAt()
        );
    }
}
