package com.lankapay.notification.channel;

import com.lankapay.identity.domain.User;
import com.lankapay.notification.domain.Notification;
import com.lankapay.notification.domain.NotificationChannelType;
import com.lankapay.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-app notification delivery channel.
 * Stores notifications in ntf_notifications for user retrieval via GET /notifications.
 */
@Component
public class InAppNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(InAppNotificationChannel.class);

    private final NotificationRepository notificationRepository;
    private boolean simulateFailure = false;

    public InAppNotificationChannel(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.IN_APP;
    }

    @Override
    public void deliver(Notification notification, User recipient) {
        if (simulateFailure) {
            log.warn("[IN-APP CHANNEL] Simulated delivery failure for userId={}", recipient.getId());
            throw new RuntimeException("Simulated in-app notification failure");
        }

        notification.markSent();
        notificationRepository.save(notification);
        log.info("[IN-APP CHANNEL] Delivered notification id={} to user id={}",
                notification.getId(), recipient.getId());
    }

    /**
     * For test simulation of channel failures.
     */
    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }
}
