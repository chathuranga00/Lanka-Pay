package com.lankapay.city.web.dto;

import com.lankapay.city.domain.CityCategory;
import com.lankapay.city.domain.CityMerchant;
import com.lankapay.city.domain.MerchantStatus;

import java.util.List;

public record CityMerchantDto(
        String id,
        String name,
        CityCategory category,
        String area,
        String description,
        String openingHours,
        String qrReference,
        MerchantStatus status,
        boolean isFavourite,
        List<CityOfferDto> offers
) {
    public static CityMerchantDto from(CityMerchant m, boolean isFavourite, List<CityOfferDto> offers) {
        return new CityMerchantDto(
                m.id(),
                m.name(),
                m.category(),
                m.area(),
                m.description(),
                m.openingHours(),
                m.qrReference(),
                m.status(),
                isFavourite,
                offers != null ? offers : List.of()
        );
    }
}
