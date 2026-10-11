package com.lankapay.admin.web.dto;

public record ComponentHealthResponse(
        String name,
        String status,
        boolean simulated,
        Long latencyMs,
        String message
) {}
