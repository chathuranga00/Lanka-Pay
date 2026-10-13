package com.lankapay.support.web.dto;

import com.lankapay.support.domain.SupportCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        SupportCategory category,
        @Size(max = 200) String subject,
        @NotBlank @Size(max = 2000) String initialMessage,
        String attachedTransactionRef
) {}
