package com.lankapay.admin.web.dto;

import com.lankapay.admin.domain.FraudAlertStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateFraudAlertStatusRequest(
        @NotNull(message = "status is required")
        FraudAlertStatus status,

        String resolutionNotes
) {}
