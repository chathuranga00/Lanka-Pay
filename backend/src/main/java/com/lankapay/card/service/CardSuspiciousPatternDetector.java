package com.lankapay.card.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.identity.domain.User;
import com.lankapay.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects and alerts on suspicious activity patterns:
 * - High frequency of failed card OTP verifications.
 * - High frequency of card details reveals in a short time window.
 */
@Component
public class CardSuspiciousPatternDetector {

    private static final Logger log = LoggerFactory.getLogger(CardSuspiciousPatternDetector.class);

    private static final int FAILED_VERIFICATION_THRESHOLD = 3;
    private static final long FAILED_VERIFICATION_WINDOW_SECONDS = 900; // 15 mins

    private static final int REVEAL_THRESHOLD = 3;
    private static final long REVEAL_WINDOW_SECONDS = 600; // 10 mins

    private final NotificationService notificationService;
    private final AuditService        auditService;

    private final ConcurrentHashMap<Long, Deque<Instant>> failedVerifications = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Deque<Instant>> reveals = new ConcurrentHashMap<>();

    public CardSuspiciousPatternDetector(NotificationService notificationService,
                                         AuditService auditService) {
        this.notificationService = notificationService;
        this.auditService        = auditService;
    }

    /**
     * Record a failed verification attempt. Triggers security alert if threshold is met.
     */
    public void recordFailedVerification(User user, Long cardId, String clientIp) {
        if (user == null || user.getId() == null) return;
        Long userId = user.getId();
        Instant now = Instant.now();

        Deque<Instant> timestamps = failedVerifications.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            timestamps.addLast(now);
            Instant cutoff = now.minusSeconds(FAILED_VERIFICATION_WINDOW_SECONDS);
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.removeFirst();
            }

            if (timestamps.size() >= FAILED_VERIFICATION_THRESHOLD) {
                log.warn("[SECURITY ALERT] Suspicious failed verification pattern for userId={} cardId={}", userId, cardId);
                auditService.log(userId, "CARD_SUSPICIOUS_VERIFICATION_ATTEMPTS", "CARD", cardId,
                        "{\"failedAttempts\":" + timestamps.size() + "}", clientIp);
                notificationService.notifySuspiciousCardActivity(user,
                        "Multiple failed verification attempts detected on your card.");
            }
        }
    }

    /**
     * Record a successful card reveal. Triggers security alert if reveals exceed threshold in window.
     */
    public void recordReveal(User user, Long cardId, String clientIp) {
        if (user == null || user.getId() == null) return;
        Long userId = user.getId();
        Instant now = Instant.now();

        Deque<Instant> timestamps = reveals.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            timestamps.addLast(now);
            Instant cutoff = now.minusSeconds(REVEAL_WINDOW_SECONDS);
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.removeFirst();
            }

            if (timestamps.size() > REVEAL_THRESHOLD) {
                log.warn("[SECURITY ALERT] Suspicious card reveal frequency for userId={} cardId={}", userId, cardId);
                auditService.log(userId, "CARD_SUSPICIOUS_REVEAL_PATTERN", "CARD", cardId,
                        "{\"revealCount\":" + timestamps.size() + "}", clientIp);
                notificationService.notifySuspiciousCardActivity(user,
                        "Unusual number of card details reveals detected.");
            }
        }
    }
}
