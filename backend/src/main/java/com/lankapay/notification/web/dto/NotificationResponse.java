package com.lankapay.notification.web.dto;

import com.lankapay.notification.domain.Notification;
import com.lankapay.notification.domain.NotificationChannelType;
import com.lankapay.notification.domain.NotificationStatus;
import com.lankapay.notification.domain.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Notification record response")
public record NotificationResponse(
        Long id,
        NotificationChannelType channel,
        NotificationType type,
        String title,
        String message,
        NotificationStatus status,
        boolean read,
        Instant readAt,
        Instant createdAt,
        boolean simulated
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getChannel(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getStatus(),
                n.isRead(),
                n.getReadAt(),
                n.getCreatedAt(),
                n.isSimulated()
        );
    }
}
