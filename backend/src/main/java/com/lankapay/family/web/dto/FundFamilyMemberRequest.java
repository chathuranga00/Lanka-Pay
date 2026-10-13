package com.lankapay.family.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class FundFamilyMemberRequest {

    @NotNull(message = "Funding amount is required")
    @Positive(message = "Funding amount must be strictly positive")
    private Long amountMinor;

    private String sourceAccount;

    private String note;

    public FundFamilyMemberRequest() {}

    public FundFamilyMemberRequest(Long amountMinor, String sourceAccount, String note) {
        this.amountMinor = amountMinor;
        this.sourceAccount = sourceAccount;
        this.note = note;
    }

    public Long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(Long amountMinor) {
        this.amountMinor = amountMinor;
    }

    public String getSourceAccount() {
        return sourceAccount;
    }

    public void setSourceAccount(String sourceAccount) {
        this.sourceAccount = sourceAccount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
