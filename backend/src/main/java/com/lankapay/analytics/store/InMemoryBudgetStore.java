package com.lankapay.analytics.store;

import com.lankapay.analytics.domain.Budget;
import com.lankapay.analytics.domain.BudgetStore;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryBudgetStore implements BudgetStore {

    private final ConcurrentHashMap<String, Budget> storage = new ConcurrentHashMap<>();

    private String key(Long userId, String month) {
        return userId + ":" + (month != null ? month.trim() : "");
    }

    @Override
    public Budget save(Budget budget) {
        if (budget.getId() == null) {
            budget.setId(UUID.randomUUID().toString());
        }
        storage.put(key(budget.getUserId(), budget.getMonth()), budget);
        return budget;
    }

    @Override
    public Optional<Budget> findByUserIdAndMonth(Long userId, String month) {
        return Optional.ofNullable(storage.get(key(userId, month)));
    }
}
