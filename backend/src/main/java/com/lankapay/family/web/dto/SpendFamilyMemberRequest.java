package com.lankapay.family.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class SpendFamilyMemberRequest {

    @NotNull(message = "Spend amount is required")
    @Positive(message = "Spend amount must be strictly positive")
    private Long amountMinor;

    @NotBlank(message = "Category is required")
    private String category;

    @NotBlank(message = "Merchant name is required")
    private String merchantName;

    private String note;

    public SpendFamilyMemberRequest() {}

    public SpendFamilyMemberRequest(Long amountMinor, String category, String merchantName, String note) {
        this.amountMinor = amountMinor;
        this.category = category;
        this.merchantName = merchantName;
        this.note = note;
    }

    public Long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(Long amountMinor) {
        this.amountMinor = amountMinor;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
