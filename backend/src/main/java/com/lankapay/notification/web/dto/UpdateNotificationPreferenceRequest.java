package com.lankapay.notification.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body to update notification preferences")
public record UpdateNotificationPreferenceRequest(
        @Schema(description = "Enable or disable in-app notifications", example = "true")
        Boolean inAppEnabled,

        @Schema(description = "Enable or disable push notifications", example = "true")
        Boolean pushEnabled,

        @Schema(description = "Enable or disable email notifications", example = "true")
        Boolean emailEnabled,

        @Schema(description = "Enable or disable SMS notifications", example = "true")
        Boolean smsEnabled,

        @Schema(description = "Enable or disable payment alerts", example = "true")
        Boolean paymentAlerts,

        @Schema(description = "Enable or disable security alerts", example = "true")
        Boolean securityAlerts
) {}
