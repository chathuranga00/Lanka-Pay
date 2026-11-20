package com.lankapay.analytics.domain;

import java.util.Optional;

public interface UserTransactionCategoryStore {

    UserTransactionCategory save(UserTransactionCategory record);

    Optional<UserTransactionCategory> find(Long userId, String transactionType, Long transactionId);

    void delete(Long userId, String transactionType, Long transactionId);
}
