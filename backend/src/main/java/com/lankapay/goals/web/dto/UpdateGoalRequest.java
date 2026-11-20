package com.lankapay.goals.web.dto;

import com.lankapay.goals.domain.GoalStatus;

import java.time.LocalDate;

public record UpdateGoalRequest(
        String name,
        Long targetMinor,
        LocalDate deadline,
        String sourceAccount,
        GoalStatus status
) {}
