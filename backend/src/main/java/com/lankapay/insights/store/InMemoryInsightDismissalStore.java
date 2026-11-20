package com.lankapay.insights.store;

import com.lankapay.insights.domain.InsightDismissalStore;
import com.lankapay.insights.domain.InsightType;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory store for tracking dismissed insight types per user.
 */
@Repository
public class InMemoryInsightDismissalStore implements InsightDismissalStore {

    private final ConcurrentHashMap<Long, Set<InsightType>> userDismissals = new ConcurrentHashMap<>();

    @Override
    public Set<InsightType> getDismissedTypes(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        Set<InsightType> set = userDismissals.get(userId);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }

    @Override
    public void dismissType(Long userId, InsightType type) {
        if (userId == null || type == null) {
            return;
        }
        userDismissals.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(type);
    }

    @Override
    public void undismissType(Long userId, InsightType type) {
        if (userId == null || type == null) {
            return;
        }
        Set<InsightType> set = userDismissals.get(userId);
        if (set != null) {
            set.remove(type);
        }
    }

    @Override
    public void clearDismissals(Long userId) {
        if (userId != null) {
            userDismissals.remove(userId);
        }
    }
}
