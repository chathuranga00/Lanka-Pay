package com.lankapay.scheduled.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScheduledRunStore {

    ScheduledRunRecord save(ScheduledRunRecord record);

    Optional<ScheduledRunRecord> findById(Long id);

    Optional<ScheduledRunRecord> findByScheduleIdAndRunDate(Long scheduleId, LocalDate runDate);

    Optional<ScheduledRunRecord> findByIdempotencyKey(String idempotencyKey);

    List<ScheduledRunRecord> findByScheduleId(Long scheduleId);

    /**
     * Atomically acquires an exclusive lock for running a schedule on a specific run date.
     * Prevents duplicate runs across concurrent instances or jobs.
     *
     * @return true if the lock was acquired, false if already locked or already run
     */
    boolean acquireRunLock(Long scheduleId, LocalDate runDate);

    /**
     * Releases the run lock.
     */
    void releaseRunLock(Long scheduleId, LocalDate runDate);
}
