package com.lankapay.scheduled.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lankapay.transfer.domain.FundingSource;

import java.time.Instant;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScheduledPayment(
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
        Instant lastReminderSentAt,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
    public ScheduledPayment withStatus(ScheduleStatus newStatus, Instant updated) {
        return new ScheduledPayment(
                id, userId, type, payee, amountMinor, currency, isBillAmount, fundingSource,
                frequency, startDate, endDate, nextRunAt, newStatus, consecutiveFailures,
                lastReminderSentAt, version + 1, createdAt, updated
        );
    }

    public ScheduledPayment withNextRun(Instant newNextRunAt, ScheduleStatus newStatus, int newConsecutiveFailures, Instant updated) {
        return new ScheduledPayment(
                id, userId, type, payee, amountMinor, currency, isBillAmount, fundingSource,
                frequency, startDate, endDate, newNextRunAt, newStatus, newConsecutiveFailures,
                lastReminderSentAt, version + 1, createdAt, updated
        );
    }

    public ScheduledPayment withReminderSent(Instant reminderSentAt) {
        return new ScheduledPayment(
                id, userId, type, payee, amountMinor, currency, isBillAmount, fundingSource,
                frequency, startDate, endDate, nextRunAt, status, consecutiveFailures,
                reminderSentAt, version, createdAt, updatedAt
        );
    }

    public ScheduledPayment withUpdate(
            ScheduledPayee newPayee,
            Long newAmountMinor,
            boolean newIsBillAmount,
            FundingSource newFundingSource,
            ScheduleFrequency newFrequency,
            LocalDate newEndDate,
            Instant newNextRunAt,
            ScheduleStatus newStatus,
            Instant updated
    ) {
        return new ScheduledPayment(
                id, userId, type,
                newPayee != null ? newPayee : payee,
                newAmountMinor != null ? newAmountMinor : amountMinor,
                currency,
                newIsBillAmount,
                newFundingSource != null ? newFundingSource : fundingSource,
                newFrequency != null ? newFrequency : frequency,
                startDate,
                newEndDate != null ? newEndDate : endDate,
                newNextRunAt != null ? newNextRunAt : nextRunAt,
                newStatus != null ? newStatus : status,
                consecutiveFailures,
                lastReminderSentAt,
                version + 1,
                createdAt,
                updated
        );
    }
}
