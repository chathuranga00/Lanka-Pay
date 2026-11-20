package com.lankapay.insights.domain;

import java.util.Set;

/**
 * Storage port interface for user-dismissed insight types.
 */
public interface InsightDismissalStore {

    Set<InsightType> getDismissedTypes(Long userId);

    void dismissType(Long userId, InsightType type);

    void undismissType(Long userId, InsightType type);

    void clearDismissals(Long userId);
}
