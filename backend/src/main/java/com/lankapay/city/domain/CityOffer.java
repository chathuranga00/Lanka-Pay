package com.lankapay.city.domain;

/**
 * City offer linked to a merchant.
 * Points multiplier or discount label shown as information only.
 */
public record CityOffer(
        String id,
        String merchantId,
        String title,
        String description,
        Double pointsMultiplier,
        String discountLabel,
        boolean active,
        String terms
) {
}
