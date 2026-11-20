package com.lankapay.admin.web.dto;

public record UpdateBankConfigRequest(
        String displayName,
        Boolean active
) {}
