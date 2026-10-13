package com.lankapay.feature.web.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateFeatureRequest(
        @NotNull(message = "enabled status is required")
        Boolean enabled
) {}
