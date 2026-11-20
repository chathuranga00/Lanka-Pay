package com.lankapay.notification.domain;

import java.time.Instant;

/**
 * Pure domain model representing a user notification.
 * Contains no JPA or database-specific annotations.
 */
public record NotificationRecord(
        Long id,
        Long userId,
        NotificationChannelType channel,
        NotificationType type,
        String title,
        String message,
        NotificationStatus status,
        String failureReason,
        boolean read,
        Instant readAt,
        Instant createdAt,
        boolean simulated
) {
    public NotificationRecord(Long userId, NotificationChannelType channel, NotificationType type, String title, String message) {
        this(null, userId, channel, type, title, message, NotificationStatus.PENDING, null, false, null, Instant.now(), false);
    }

    public NotificationRecord withStatus(NotificationStatus newStatus, String newFailureReason) {
        return new NotificationRecord(
                id, userId, channel, type, title, message, newStatus, newFailureReason, read, readAt, createdAt, simulated
        );
    }

    public NotificationRecord markSent() {
        return withStatus(NotificationStatus.SENT, null);
    }

    public NotificationRecord markFailed(String reason) {
        return withStatus(NotificationStatus.FAILED, reason);
    }

    public NotificationRecord markRead(Instant now) {
        return new NotificationRecord(
                id, userId, channel, type, title, message, status, failureReason, true, now, createdAt, simulated
        );
    }
}
