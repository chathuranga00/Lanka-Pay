package com.lankapay.goals.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port interface for savings goals and simulated ledger persistence.
 */
public interface GoalStore {

    GoalModel save(GoalModel goal);

    Optional<GoalModel> findById(String id);

    List<GoalModel> findByUserId(Long userId);

    boolean delete(String id, Long userId);

    GoalLedgerEntry appendEntry(GoalLedgerEntry entry);

    List<GoalLedgerEntry> findEntriesByGoalId(String goalId, Long userId);

    Optional<GoalLedgerEntry> findEntryByIdempotencyKey(Long userId, String idempotencyKey);

    Object getGoalLock(String goalId);

    void clear();
}
