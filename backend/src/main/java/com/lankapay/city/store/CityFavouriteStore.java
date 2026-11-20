package com.lankapay.city.store;

import java.util.Set;

public interface CityFavouriteStore {
    Set<String> getFavouriteMerchantIds(Long userId);
    boolean isFavourite(Long userId, String merchantId);
    void addFavourite(Long userId, String merchantId);
    void removeFavourite(Long userId, String merchantId);
}
