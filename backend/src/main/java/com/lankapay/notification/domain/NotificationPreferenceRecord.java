package com.lankapay.notification.domain;

import java.time.Instant;

/**
 * Pure domain model representing user notification preferences.
 * Contains no JPA or database-specific annotations.
 */
public record NotificationPreferenceRecord(
        Long id,
        Long userId,
        boolean inAppEnabled,
        boolean pushEnabled,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean paymentAlerts,
        boolean securityAlerts,
        Instant updatedAt
) {
    public static NotificationPreferenceRecord defaultForUser(Long userId) {
        return new NotificationPreferenceRecord(
                null, userId, true, true, false, false, true, true, Instant.now()
        );
    }

    public NotificationPreferenceRecord update(
            boolean inAppEnabled,
            boolean pushEnabled,
            boolean emailEnabled,
            boolean smsEnabled,
            boolean paymentAlerts,
            boolean securityAlerts) {
        return new NotificationPreferenceRecord(
                id, userId, inAppEnabled, pushEnabled, emailEnabled, smsEnabled,
                paymentAlerts, securityAlerts, Instant.now()
        );
    }
}
