package com.lankapay.bill.web.dto;

import jakarta.validation.constraints.NotBlank;

public record BillLookupRequest(
        @NotBlank(message = "billerId is required")
        String billerId,

        @NotBlank(message = "customerReference is required")
        String customerReference
) {}
