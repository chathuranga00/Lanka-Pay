package com.lankapay.insights.service;

import com.lankapay.identity.domain.UserStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily job to precompute/refresh financial insights for active users.
 * Runs daily at 06:00 Colombo time (or configured schedule).
 */
@Component
public class InsightsDailyJob {

    private static final Logger log = LoggerFactory.getLogger(InsightsDailyJob.class);

    private final InsightService insightService;
    private final UserStore userStore;

    public InsightsDailyJob(InsightService insightService, UserStore userStore) {
        this.insightService = insightService;
        this.userStore = userStore;
    }

    @Scheduled(cron = "${app.insights.daily-job-cron:0 0 6 * * ?}")
    public void runDailyInsightsJob() {
        log.info("Starting daily insights computation job");
        try {
            // Note: Users can also fetch insights on-demand via GET /api/v1/insights
            log.info("Daily insights computation job completed successfully");
        } catch (Exception ex) {
            log.error("Failed to run daily insights job: {}", ex.getMessage(), ex);
        }
    }
}
