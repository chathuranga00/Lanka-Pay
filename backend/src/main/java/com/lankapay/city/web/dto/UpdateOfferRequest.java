package com.lankapay.city.web.dto;

public record UpdateOfferRequest(
        String title,
        String description,
        Double pointsMultiplier,
        String discountLabel,
        Boolean active,
        String terms
) {
}
