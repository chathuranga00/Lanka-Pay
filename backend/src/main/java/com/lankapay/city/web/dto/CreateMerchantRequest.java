package com.lankapay.city.web.dto;

import com.lankapay.city.domain.CityCategory;
import com.lankapay.city.domain.MerchantStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateMerchantRequest(
        String id,
        @NotBlank(message = "Merchant name is required")
        String name,
        @NotNull(message = "Category is required")
        CityCategory category,
        @NotBlank(message = "Area is required")
        String area,
        String description,
        String openingHours,
        String qrReference,
        MerchantStatus status
) {
}
