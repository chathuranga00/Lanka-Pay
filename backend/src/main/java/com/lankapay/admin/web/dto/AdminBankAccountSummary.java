package com.lankapay.admin.web.dto;

import com.lankapay.bank.domain.BankAccount;
import com.lankapay.bank.domain.BankAccountStatus;

public record AdminBankAccountSummary(
        Long id,
        String bankCode,
        String maskedNumber,
        String accountName,
        BankAccountStatus status,
        boolean isDefault
) {
    public static AdminBankAccountSummary from(BankAccount account) {
        if (account == null) return null;
        return new AdminBankAccountSummary(
                account.getId(),
                account.getBankCode(),
                account.getMaskedNumber(),
                account.getAccountName(),
                account.getStatus(),
                account.isDefault()
        );
    }

    public static AdminBankAccountSummary from(com.lankapay.bank.domain.LinkedAccount account) {
        if (account == null) return null;
        return new AdminBankAccountSummary(
                account.id(),
                account.bankCode(),
                account.maskedNumber(),
                account.accountName(),
                account.status(),
                account.isDefault()
        );
    }
}

