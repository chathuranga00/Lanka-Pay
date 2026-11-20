package com.lankapay.feature.web.dto;

import java.util.Map;

/**
 * Response payload describing enabled Super App modules and environmental context.
 */
public record FeaturesResponse(
        Map<String, Boolean> features,
        String environment,
        String disclaimer
) {}
