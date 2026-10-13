package com.lankapay.family.web.dto;

import com.lankapay.family.domain.FamilyTransaction;
import com.lankapay.family.domain.FamilyTransactionStatus;
import com.lankapay.family.domain.FamilyTransactionType;

import java.time.Instant;

public record FamilyTransactionDto(
        String id,
        String memberId,
        FamilyTransactionType type,
        long amountMinor,
        long balanceAfterMinor,
        String category,
        String merchantOrSource,
        String note,
        FamilyTransactionStatus status,
        String declineReason,
        Instant timestamp
) {
    public static FamilyTransactionDto fromDomain(FamilyTransaction t) {
        if (t == null) return null;
        return new FamilyTransactionDto(
                t.getId(),
                t.getMemberId(),
                t.getType(),
                t.getAmountMinor(),
                t.getBalanceAfterMinor(),
                t.getCategory(),
                t.getMerchantOrSource(),
                t.getNote(),
                t.getStatus(),
                t.getDeclineReason(),
                t.getTimestamp()
        );
    }
}
