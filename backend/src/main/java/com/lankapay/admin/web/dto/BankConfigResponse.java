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
        if (inst == null) return null;
        return new BankConfigResponse(
                inst.getId(),
                inst.getBankCode(),
                inst.getDisplayName(),
                inst.isSimulated(),
                inst.isActive(),
                inst.getCreatedAt()
        );
    }

    public static BankConfigResponse from(com.lankapay.bank.domain.BankInstitutionRecord inst) {
        if (inst == null) return null;
        return new BankConfigResponse(
                inst.id(),
                inst.bankCode(),
                inst.displayName(),
                inst.simulated(),
                inst.active(),
                inst.createdAt()
        );
    }
}

