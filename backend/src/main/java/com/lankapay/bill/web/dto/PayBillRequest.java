package com.lankapay.bill.web.dto;

import com.lankapay.transfer.domain.FundingSource;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PayBillRequest(
        @NotBlank(message = "billerId is required")
        String billerId,

        @NotBlank(message = "customerReference is required")
        String customerReference,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "1.00", message = "amount must be at least 1.00 LKR")
        BigDecimal amount,

        String currency,

        FundingSource fundingSource,

        String pin,

        String note,

        boolean saveBiller,

        String billerNickname
) {}
