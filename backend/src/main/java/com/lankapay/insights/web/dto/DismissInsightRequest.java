package com.lankapay.insights.web.dto;

import com.lankapay.insights.domain.InsightType;
import jakarta.validation.constraints.NotNull;

public record DismissInsightRequest(
        @NotNull(message = "Insight type is required")
        InsightType type
) {}
