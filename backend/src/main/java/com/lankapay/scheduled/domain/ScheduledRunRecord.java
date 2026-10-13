package com.lankapay.scheduled.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScheduledRunRecord(
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
) {}
