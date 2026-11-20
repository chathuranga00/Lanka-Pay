package com.lankapay.notification.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Optional;

/**
 * Storage port interface for notification and preference operations.
 * Decouples services from specific database persistence technologies.
 */
public interface NotificationStore {

    NotificationRecord saveNotification(NotificationRecord record);

    Optional<NotificationRecord> findNotificationByIdAndUserId(Long id, Long userId);

    Page<NotificationRecord> findNotificationsByUserId(Long userId, Pageable pageable);

    Page<NotificationRecord> findNotificationsByUserIdAndRead(Long userId, boolean read, Pageable pageable);

    Page<NotificationRecord> findNotificationsByUserIdAndType(Long userId, NotificationType type, Pageable pageable);

    long countUnreadNotifications(Long userId);

    int markAllNotificationsAsReadForUser(Long userId, Instant now);

    int deleteNotificationsOlderThan(Instant cutoff);

    Optional<NotificationPreferenceRecord> findPreferenceByUserId(Long userId);

    NotificationPreferenceRecord savePreference(NotificationPreferenceRecord preference);
}
