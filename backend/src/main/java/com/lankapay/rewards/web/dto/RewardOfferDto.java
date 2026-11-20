package com.lankapay.rewards.web.dto;

import com.lankapay.rewards.domain.RewardOffer;
import java.time.Instant;

public record RewardOfferDto(
        String id,
        String title,
        String description,
        String merchantOrCategory,
        Double pointsMultiplier,
        Integer fixedPoints,
        Instant startAt,
        Instant endAt,
        Integer maxPointsCap,
        String terms,
        boolean active
) {
    public static RewardOfferDto from(RewardOffer o) {
        return new RewardOfferDto(
                o.id(),
                o.title(),
                o.description(),
                o.merchantOrCategory(),
                o.pointsMultiplier(),
                o.fixedPoints(),
                o.startAt(),
                o.endAt(),
                o.maxPointsCap(),
                o.terms(),
                o.active()
        );
    }
}
