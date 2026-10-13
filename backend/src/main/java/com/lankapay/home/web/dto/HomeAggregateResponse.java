package com.lankapay.home.web.dto;

import com.lankapay.home.domain.HomeSection;

import java.util.List;

/**
 * Resilient one-call Home aggregator payload.
 * Each section is independently nullable with an error marker so that a failure
 * in any single section never blanks the dashboard.
 */
public record HomeAggregateResponse(
        HomeSection<GreetingData> greeting,
        HomeSection<NotificationData> unreadNotifications,
        HomeSection<WalletData> wallet,
        HomeSection<List<RecentActivityItem>> recentActivity,
        HomeSection<Object> rewardsPoints,
        HomeSection<Object> stories,
        HomeSection<Object> spendSummary,
        HomeSection<Object> nudge,
        HomeSection<List<ShortcutItem>> shortcuts
) {}
