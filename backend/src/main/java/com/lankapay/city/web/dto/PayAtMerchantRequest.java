package com.lankapay.city.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PayAtMerchantRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "1.00", message = "Amount must be at least LKR 1.00")
        BigDecimal amount,

        String pin,

        String note,

        Long sourceAccountId
) {
}
