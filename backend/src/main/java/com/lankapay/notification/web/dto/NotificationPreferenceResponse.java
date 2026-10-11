package com.lankapay.notification.web.dto;

import com.lankapay.notification.domain.NotificationPreference;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "User notification preferences")
public record NotificationPreferenceResponse(
        boolean inAppEnabled,
        boolean pushEnabled,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean paymentAlerts,
        boolean securityAlerts,
        Instant updatedAt
) {
    public static NotificationPreferenceResponse from(NotificationPreference pref) {
        return new NotificationPreferenceResponse(
                pref.isInAppEnabled(),
                pref.isPushEnabled(),
                pref.isEmailEnabled(),
                pref.isSmsEnabled(),
                pref.isPaymentAlerts(),
                pref.isSecurityAlerts(),
                pref.getUpdatedAt()
        );
    }
}
