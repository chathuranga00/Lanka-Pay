package com.lankapay.city.domain;

/**
 * City merchant domain record.
 * Contains no GPS coordinates or location storage.
 */
public record CityMerchant(
        String id,
        String name,
        CityCategory category,
        String area,
        String description,
        String openingHours,
        String qrReference,
        MerchantStatus status
) {
    public boolean isActive() {
        return status == MerchantStatus.ACTIVE;
    }
}
