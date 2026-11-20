package com.lankapay.rewards.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Offer definition for rewards point multipliers or bonus points.
 */
public record RewardOffer(
        String id,
        String title,
        String description,
        String merchantOrCategory,
        Double pointsMultiplier, // e.g. 2.0x, or null if fixed
        Integer fixedPoints,     // e.g. 50 pts, or null if multiplier
        Instant startAt,
        Instant endAt,
        Integer maxPointsCap,    // max bonus points per transaction or lifetime
        String terms,
        boolean active
) {
    public boolean isValidAt(Instant now) {
        if (!active) return false;
        if (startAt != null && now.isBefore(startAt)) return false;
        if (endAt != null && now.isAfter(endAt)) return false;
        return true;
    }
}
