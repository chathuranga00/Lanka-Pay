package com.lankapay.rewards.store;

import com.lankapay.rewards.domain.RewardOffer;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory offer store for active rewards offers.
 */
public class InMemoryRewardOfferStore {

    private final Map<String, RewardOffer> offers = new ConcurrentHashMap<>();

    public void save(RewardOffer offer) {
        offers.put(offer.id(), offer);
    }

    public Optional<RewardOffer> findById(String id) {
        return Optional.ofNullable(offers.get(id));
    }

    public List<RewardOffer> findAll() {
        return new CopyOnWriteArrayList<>(offers.values());
    }

    public void clear() {
        offers.clear();
    }
}
