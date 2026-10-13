package com.lankapay.city.web.dto;

import com.lankapay.city.domain.CityCategory;
import com.lankapay.city.domain.MerchantStatus;

public record UpdateMerchantRequest(
        String name,
        CityCategory category,
        String area,
        String description,
        String openingHours,
        String qrReference,
        MerchantStatus status
) {
}
