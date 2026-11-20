package com.lankapay.home.web.dto;

public record LinkedAccountSummary(
        Long id,
        String bankCode,
        String bankName,
        String maskedAccountNumber,
        String accountHolderName,
        boolean isDefault
) {}
