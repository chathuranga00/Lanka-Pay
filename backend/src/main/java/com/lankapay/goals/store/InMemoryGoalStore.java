package com.lankapay.goals.store;

import com.lankapay.goals.domain.GoalLedgerEntry;
import com.lankapay.goals.domain.GoalModel;
import com.lankapay.goals.domain.GoalStore;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe In-Memory implementation of GoalStore.
 * Ensures per-goal synchronization monitor for atomic balance checks and append-only ledger entries.
 */
@Component
public class InMemoryGoalStore implements GoalStore {

    // Goal ID -> GoalModel
    private final Map<String, GoalModel> goals = new ConcurrentHashMap<>();

    // Goal ID -> List of append-only ledger entries (ordered oldest to newest)
    private final Map<String, List<GoalLedgerEntry>> goalEntries = new ConcurrentHashMap<>();

    // User ID + IdempotencyKey -> GoalLedgerEntry
    private final Map<String, GoalLedgerEntry> idempotencyRecords = new ConcurrentHashMap<>();

    // Per-goal lock monitors for atomic balance transitions
    private final Map<String, Object> goalLocks = new ConcurrentHashMap<>();

    public Object getGoalLock(String goalId) {
        return goalLocks.computeIfAbsent(goalId, k -> new Object());
    }

    @Override
    public GoalModel save(GoalModel goal) {
        synchronized (getGoalLock(goal.id())) {
            goals.put(goal.id(), goal);
            return goal;
        }
    }

    @Override
    public Optional<GoalModel> findById(String id) {
        return Optional.ofNullable(goals.get(id));
    }

    @Override
    public List<GoalModel> findByUserId(Long userId) {
        if (userId == null) return List.of();
        List<GoalModel> result = new ArrayList<>();
        for (GoalModel goal : goals.values()) {
            if (userId.equals(goal.userId())) {
                result.add(goal);
            }
        }
        result.sort(Comparator.comparing(GoalModel::createdAt).reversed());
        return Collections.unmodifiableList(result);
    }

    @Override
    public boolean delete(String id, Long userId) {
        synchronized (getGoalLock(id)) {
            GoalModel existing = goals.get(id);
            if (existing != null && (userId == null || userId.equals(existing.userId()))) {
                goals.remove(id);
                goalEntries.remove(id);
                return true;
            }
            return false;
        }
    }

    @Override
    public GoalLedgerEntry appendEntry(GoalLedgerEntry entry) {
        synchronized (getGoalLock(entry.goalId())) {
            List<GoalLedgerEntry> entries = goalEntries.computeIfAbsent(entry.goalId(), k -> new CopyOnWriteArrayList<>());
            entries.add(entry);
            if (entry.idempotencyKey() != null && !entry.idempotencyKey().isBlank()) {
                String key = makeIdempotencyKey(entry.userId(), entry.idempotencyKey());
                idempotencyRecords.put(key, entry);
            }
            return entry;
        }
    }

    @Override
    public List<GoalLedgerEntry> findEntriesByGoalId(String goalId, Long userId) {
        GoalModel goal = goals.get(goalId);
        if (goal == null || (userId != null && !userId.equals(goal.userId()))) {
            return List.of();
        }
        List<GoalLedgerEntry> entries = goalEntries.getOrDefault(goalId, List.of());
        List<GoalLedgerEntry> copy = new ArrayList<>(entries);
        Collections.reverse(copy);
        return Collections.unmodifiableList(copy);
    }

    @Override
    public Optional<GoalLedgerEntry> findEntryByIdempotencyKey(Long userId, String idempotencyKey) {
        if (userId == null || idempotencyKey == null) return Optional.empty();
        return Optional.ofNullable(idempotencyRecords.get(makeIdempotencyKey(userId, idempotencyKey)));
    }

    @Override
    public void clear() {
        goals.clear();
        goalEntries.clear();
        idempotencyRecords.clear();
        goalLocks.clear();
    }

    private String makeIdempotencyKey(Long userId, String key) {
        return userId + ":" + key;
    }
}
