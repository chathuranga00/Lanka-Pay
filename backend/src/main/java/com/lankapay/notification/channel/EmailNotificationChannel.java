package com.lankapay.notification.channel;

import com.lankapay.identity.domain.User;
import com.lankapay.notification.domain.Notification;
import com.lankapay.notification.domain.NotificationChannelType;
import com.lankapay.notification.service.NotificationMessageFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Email notification channel stub (for test mode & prototype).
 * Delivers privacy-safe notifications to user's registered email address.
 * In prototype/test mode, writes to the log with simulated=true.
 */
@Component
public class EmailNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    private boolean simulateFailure = false;

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public void deliver(Notification notification, User recipient) {
        if (simulateFailure) {
            log.warn("[EMAIL STUB] Simulated SMTP server connection failure for userId={}", recipient.getId());
            throw new RuntimeException("Simulated SMTP server timeout 504");
        }

        // Validate lock-screen preview privacy
        NotificationMessageFormatter.assertPrivacySafe(notification.getMessage());

        log.info("[EMAIL STUB] user={} phone={} title='{}' body='{}' (simulated=true)",
                recipient.getId(), recipient.getMaskedPhone(), notification.getTitle(), notification.getMessage());
    }

    /**
     * For testing failure handling and fallback.
     */
    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }
}
