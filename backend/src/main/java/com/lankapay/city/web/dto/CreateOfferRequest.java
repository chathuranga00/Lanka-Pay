package com.lankapay.city.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateOfferRequest(
        String id,
        @NotBlank(message = "Merchant ID is required")
        String merchantId,
        @NotBlank(message = "Title is required")
        String title,
        String description,
        Double pointsMultiplier,
        String discountLabel,
        Boolean active,
        String terms
) {
}
