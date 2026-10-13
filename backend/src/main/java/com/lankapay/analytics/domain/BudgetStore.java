package com.lankapay.analytics.domain;

import java.util.Optional;

public interface BudgetStore {

    Budget save(Budget budget);

    Optional<Budget> findByUserIdAndMonth(Long userId, String month);
}
