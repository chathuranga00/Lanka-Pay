package com.lankapay.notification.channel;

import com.lankapay.identity.domain.User;
import com.lankapay.notification.domain.Notification;
import com.lankapay.notification.domain.NotificationChannelType;

/**
 * Interface for notification delivery channels (in-app, push, email, etc.).
 * Implementations deliver privacy-safe notifications to users.
 */
public interface NotificationChannel {

    /**
     * The channel type handled by this implementation.
     */
    NotificationChannelType getChannelType();

    /**
     * Deliver the notification to the target recipient.
     *
     * @param notification The notification entity containing title and privacy-safe message
     * @param recipient    The target user
     * @throws RuntimeException on delivery failure (must be caught by NotificationService to avoid breaking business flows)
     */
    void deliver(Notification notification, User recipient);
}
