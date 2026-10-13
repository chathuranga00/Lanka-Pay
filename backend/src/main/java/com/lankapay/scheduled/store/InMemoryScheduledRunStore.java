package com.lankapay.scheduled.store;

import com.lankapay.scheduled.domain.ScheduledRunRecord;
import com.lankapay.scheduled.domain.ScheduledRunStore;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryScheduledRunStore implements ScheduledRunStore {

    private final Map<Long, ScheduledRunRecord> store = new ConcurrentHashMap<>();
    private final Map<String, Long> scheduleDateIndex = new ConcurrentHashMap<>();
    private final Map<String, Long> idempotencyIndex = new ConcurrentHashMap<>();
    private final Set<String> activeLocks = ConcurrentHashMap.newKeySet();
    private final AtomicLong idGen = new AtomicLong(5000);

    @Override
    public ScheduledRunRecord save(ScheduledRunRecord record) {
        Long id = record.id() != null ? record.id() : idGen.incrementAndGet();
        ScheduledRunRecord entity = new ScheduledRunRecord(
                id,
                record.scheduleId(),
                record.runDate(),
                record.idempotencyKey(),
                record.transactionRef(),
                record.amountMinor(),
                record.currency(),
                record.status(),
                record.failureReason(),
                record.executedAt()
        );
        store.put(id, entity);
        if (entity.scheduleId() != null && entity.runDate() != null) {
            scheduleDateIndex.put(key(entity.scheduleId(), entity.runDate()), id);
        }
        if (entity.idempotencyKey() != null) {
            idempotencyIndex.put(entity.idempotencyKey(), id);
        }
        return entity;
    }

    @Override
    public Optional<ScheduledRunRecord> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<ScheduledRunRecord> findByScheduleIdAndRunDate(Long scheduleId, LocalDate runDate) {
        Long id = scheduleDateIndex.get(key(scheduleId, runDate));
        if (id == null) return Optional.empty();
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<ScheduledRunRecord> findByIdempotencyKey(String idempotencyKey) {
        Long id = idempotencyIndex.get(idempotencyKey);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<ScheduledRunRecord> findByScheduleId(Long scheduleId) {
        return store.values().stream()
                .filter(r -> Objects.equals(r.scheduleId(), scheduleId))
                .sorted(Comparator.comparing(ScheduledRunRecord::executedAt).reversed())
                .toList();
    }

    @Override
    public synchronized boolean acquireRunLock(Long scheduleId, LocalDate runDate) {
        String k = key(scheduleId, runDate);
        if (findByScheduleIdAndRunDate(scheduleId, runDate).isPresent()) {
            return false; // already executed
        }
        return activeLocks.add(k);
    }

    @Override
    public synchronized void releaseRunLock(Long scheduleId, LocalDate runDate) {
        activeLocks.remove(key(scheduleId, runDate));
    }

    private String key(Long scheduleId, LocalDate runDate) {
        return scheduleId + ":" + runDate;
    }
}
