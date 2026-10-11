package com.lankapay.admin.web.dto;

import com.lankapay.bank.domain.BankInstitution;

import java.time.Instant;

public record BankConfigResponse(
        Long id,
        String bankCode,
        String displayName,
        boolean simulated,
        boolean active,
        Instant createdAt
) {
    public static BankConfigResponse from(BankInstitution inst) {
        return new BankConfigResponse(
                inst.getId(),
                inst.getBankCode(),
                inst.getDisplayName(),
                inst.isSimulated(),
                inst.isActive(),
                inst.getCreatedAt()
        );
    }
}
