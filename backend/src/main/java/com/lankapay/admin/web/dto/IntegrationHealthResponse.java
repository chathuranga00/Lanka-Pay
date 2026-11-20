package com.lankapay.admin.web.dto;

import java.time.Instant;
import java.util.List;

public record IntegrationHealthResponse(
        String overallStatus,
        Instant checkedAt,
        List<ComponentHealthResponse> components
) {}
