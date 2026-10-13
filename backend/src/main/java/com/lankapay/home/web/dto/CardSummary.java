package com.lankapay.home.web.dto;

public record CardSummary(
        Long id,
        String maskedNumber,
        String cardLabel,
        boolean isFrozen,
        String expiry
) {}
