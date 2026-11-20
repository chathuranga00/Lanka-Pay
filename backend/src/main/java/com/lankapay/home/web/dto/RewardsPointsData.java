package com.lankapay.home.web.dto;

/**
 * Simulated rewards points summary for the "Rewards" Info Tile.
 *
 * - pointsBalance  : current total redeemable points
 * - formattedPoints: display string, e.g. "1,250 pts"
 * - hasNewOffers   : true when there are unseen reward offers → shows the dot on the button
 */
public record RewardsPointsData(
        long pointsBalance,
        String formattedPoints,
        boolean hasNewOffers
) {}
