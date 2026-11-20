package com.lankapay.admin.web.dto;

import java.math.BigDecimal;

public record UpdateRiskRulesRequest(
        BigDecimal maxTransactionAmount,
        BigDecimal maxDailyAmount,
        Integer maxVelocityCountPerHour,
        Integer newAccountAgeHours,
        BigDecimal newAccountMaxTransactionAmount,
        BigDecimal newAccountMaxDailyAmount,
        Integer velocityWindowMinutes,
        Boolean enabled
) {}
