package com.lankapay.bill.web.dto;

import com.lankapay.bill.adapter.BillerCategory;

import java.time.Instant;

public record SavedBillerResponse(
        Long id,
        String billerId,
        String billerName,
        BillerCategory category,
        String nickname,
        String maskedReference,
        Instant createdAt
) {}
