package com.lankapay.bank.domain;

public enum BankAccountStatus {
    ACTIVE,
    REMOVED;

    public boolean isUsable() {
        return this == ACTIVE;
    }
}
