package com.lankapay.bank.domain;

import java.time.Instant;

/**
 * Domain entity for bnk_bank_institutions.
 * 3 SIMULATED banks.
 */
public class BankInstitution {

    private Long id;

    /** e.g. SIMULATED_BANK_A. Always prefixed SIMULATED_ in this prototype. */
    private String bankCode;

    private String displayName;

    /** Always true in this prototype. */
    private boolean simulated = true;

    private boolean active = true;

    private Instant createdAt = Instant.now();

    public BankInstitution() {}

    public BankInstitution(String bankCode, String displayName, boolean simulated, boolean active) {
        this.bankCode = bankCode;
        this.displayName = displayName;
        this.simulated = simulated;
        this.active = active;
    }

    public Long getId() { return id; }
    public String getBankCode() { return bankCode; }
    public String getDisplayName() { return displayName; }
    public boolean isSimulated() { return simulated; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setActive(boolean active) { this.active = active; }
}

