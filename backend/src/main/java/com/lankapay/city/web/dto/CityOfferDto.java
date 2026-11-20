package com.lankapay.city.web.dto;

import com.lankapay.city.domain.CityOffer;

public record CityOfferDto(
        String id,
        String merchantId,
        String title,
        String description,
        Double pointsMultiplier,
        String discountLabel,
        boolean active,
        String terms
) {
    public static CityOfferDto from(CityOffer o) {
        return new CityOfferDto(
                o.id(),
                o.merchantId(),
                o.title(),
                o.description(),
                o.pointsMultiplier(),
                o.discountLabel(),
                o.active(),
                o.terms()
        );
    }
}
