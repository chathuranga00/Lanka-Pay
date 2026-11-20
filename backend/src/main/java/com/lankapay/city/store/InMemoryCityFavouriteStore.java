package com.lankapay.city.store;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryCityFavouriteStore implements CityFavouriteStore {

    private final ConcurrentHashMap<Long, Set<String>> userFavourites = new ConcurrentHashMap<>();

    @Override
    public Set<String> getFavouriteMerchantIds(Long userId) {
        if (userId == null) return Collections.emptySet();
        return userFavourites.getOrDefault(userId, Collections.emptySet());
    }

    @Override
    public boolean isFavourite(Long userId, String merchantId) {
        if (userId == null || merchantId == null) return false;
        Set<String> set = userFavourites.get(userId);
        return set != null && set.contains(merchantId);
    }

    @Override
    public void addFavourite(Long userId, String merchantId) {
        if (userId == null || merchantId == null) return;
        userFavourites.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(merchantId);
    }

    @Override
    public void removeFavourite(Long userId, String merchantId) {
        if (userId == null || merchantId == null) return;
        Set<String> set = userFavourites.get(userId);
        if (set != null) {
            set.remove(merchantId);
        }
    }
}
