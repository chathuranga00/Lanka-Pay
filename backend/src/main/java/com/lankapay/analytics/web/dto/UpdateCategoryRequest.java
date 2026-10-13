package com.lankapay.analytics.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateCategoryRequest(
        String transactionType, // "TRANSFER" or "BILL"
        Long transactionId,
        @NotBlank(message = "category is required")
        String category,
        String colorHex
) {
    public UpdateCategoryRequest withTransactionId(Long id) {
        return new UpdateCategoryRequest(transactionType, id, category, colorHex);
    }
}
