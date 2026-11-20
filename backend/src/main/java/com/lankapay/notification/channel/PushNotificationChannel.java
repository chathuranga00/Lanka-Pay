package com.lankapay.notification.channel;

import com.lankapay.identity.domain.User;
import com.lankapay.notification.domain.Notification;
import com.lankapay.notification.domain.NotificationChannelType;
import com.lankapay.notification.service.NotificationMessageFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Push notification channel stub (for test mode & prototype).
 * Delivers privacy-safe notifications to device push services (e.g. FCM/APNs).
 * In prototype/test mode, writes to the log and validates lock-screen privacy constraints.
 */
@Component
public class PushNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationChannel.class);

    private boolean simulateFailure = false;

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.PUSH;
    }

    @Override
    public void deliver(Notification notification, User recipient) {
        if (simulateFailure) {
            log.warn("[PUSH STUB] Simulated push gateway timeout for userId={}", recipient.getId());
            throw new RuntimeException("Simulated push gateway network timeout");
        }

        // Validate that lock-screen push text contains NO amounts and NO account digits
        NotificationMessageFormatter.assertPrivacySafe(notification.getMessage());

        log.info("[PUSH STUB] user={} title='{}' body='{}' (simulated=true)",
                recipient.getId(), notification.getTitle(), notification.getMessage());
    }

    /**
     * For testing failure handling and fallback.
     */
    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }
}
