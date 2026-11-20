package com.lankapay.analytics.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Budget {

    private String id;
    private Long userId;
    private String month; // e.g. "2026-10"
    private Long monthlyTotalMinor;
    @Builder.Default
    private Map<String, Long> categoryBudgetsMinor = new HashMap<>();
    private boolean notified80;
    private boolean notified100;
    private Instant createdAt;
    private Instant updatedAt;
}
