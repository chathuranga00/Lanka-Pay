package com.lankapay.scheduled.store;

import com.lankapay.scheduled.domain.ScheduleStatus;
import com.lankapay.scheduled.domain.ScheduledPayment;
import com.lankapay.scheduled.domain.ScheduledPaymentStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryScheduledPaymentStore implements ScheduledPaymentStore {

    private final Map<Long, ScheduledPayment> store = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(100);

    @Override
    public ScheduledPayment save(ScheduledPayment payment) {
        Long id = payment.id() != null ? payment.id() : idGen.incrementAndGet();
        Instant now = Instant.now();
        Instant created = payment.createdAt() != null ? payment.createdAt() : now;
        ScheduledPayment entity = new ScheduledPayment(
                id,
                payment.userId(),
                payment.type(),
                payment.payee(),
                payment.amountMinor(),
                payment.currency() != null ? payment.currency() : "LKR",
                payment.isBillAmount(),
                payment.fundingSource(),
                payment.frequency(),
                payment.startDate(),
                payment.endDate(),
                payment.nextRunAt(),
                payment.status() != null ? payment.status() : ScheduleStatus.ACTIVE,
                payment.consecutiveFailures(),
                payment.lastReminderSentAt(),
                payment.version() != null ? payment.version() : 1L,
                created,
                now
        );
        store.put(id, entity);
        return entity;
    }

    @Override
    public Optional<ScheduledPayment> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<ScheduledPayment> findByUserId(Long userId) {
        return store.values().stream()
                .filter(p -> Objects.equals(p.userId(), userId))
                .sorted(Comparator.comparing(ScheduledPayment::createdAt).reversed())
                .toList();
    }

    @Override
    public List<ScheduledPayment> findDueSchedules(Instant asOf) {
        return store.values().stream()
                .filter(p -> p.status() == ScheduleStatus.ACTIVE)
                .filter(p -> p.nextRunAt() != null && !p.nextRunAt().isAfter(asOf))
                .sorted(Comparator.comparing(ScheduledPayment::nextRunAt))
                .toList();
    }

    @Override
    public List<ScheduledPayment> findUpcomingForReminders(Instant from, Instant to) {
        return store.values().stream()
                .filter(p -> p.status() == ScheduleStatus.ACTIVE)
                .filter(p -> p.nextRunAt() != null)
                .filter(p -> p.nextRunAt().isAfter(from) && !p.nextRunAt().isAfter(to))
                .toList();
    }

    @Override
    public boolean delete(Long id) {
        return store.remove(id) != null;
    }
}
