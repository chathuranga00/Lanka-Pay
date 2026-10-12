package com.lankapay.card.job;

import com.lankapay.card.service.CardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Scheduled job that periodically sweeps the cards collection
 * and transitions cards past their expiry date to status EXPIRED.
 */
@Component
public class ExpiredCardJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiredCardJob.class);

    private final CardService cardService;

    public ExpiredCardJob(CardService cardService) {
        this.cardService = cardService;
    }

    /**
     * Runs daily at midnight (or configured cron) to update expired cards.
     */
    @Scheduled(cron = "${app.cards.expiry-check-cron:0 0 0 * * ?}")
    public void runExpirySweep() {
        log.info("[CARD JOB] Starting scheduled expired-card sweep");
        try {
            int updated = cardService.expireOutdatedCards(LocalDate.now());
            log.info("[CARD JOB] Expired-card sweep finished. {} cards transitioned to EXPIRED", updated);
        } catch (Exception e) {
            log.error("[CARD JOB] Expired-card sweep failed", e);
        }
    }
}
