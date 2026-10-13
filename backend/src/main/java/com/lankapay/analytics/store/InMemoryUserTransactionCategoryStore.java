package com.lankapay.analytics.store;

import com.lankapay.analytics.domain.UserTransactionCategory;
import com.lankapay.analytics.domain.UserTransactionCategoryStore;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryUserTransactionCategoryStore implements UserTransactionCategoryStore {

    private final ConcurrentHashMap<String, UserTransactionCategory> storage = new ConcurrentHashMap<>();

    @Override
    public UserTransactionCategory save(UserTransactionCategory record) {
        storage.put(record.getKey(), record);
        return record;
    }

    @Override
    public Optional<UserTransactionCategory> find(Long userId, String transactionType, Long transactionId) {
        String key = UserTransactionCategory.buildKey(userId, transactionType, transactionId);
        return Optional.ofNullable(storage.get(key));
    }

    @Override
    public void delete(Long userId, String transactionType, Long transactionId) {
        String key = UserTransactionCategory.buildKey(userId, transactionType, transactionId);
        storage.remove(key);
    }
}
