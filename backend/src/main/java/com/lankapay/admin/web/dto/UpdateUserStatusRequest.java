package com.lankapay.admin.web.dto;

import com.lankapay.identity.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "status is required")
        UserStatus status,

        String reason
) {}
