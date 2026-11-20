package com.lankapay.support.web.dto;

import java.util.List;

public record AdminSupportConversationDetailDto(
        SupportConversationDto conversation,
        List<SupportMessageDto> messages,
        MaskedTransactionSummaryDto attachedTransaction,
        String safetyNotice
) {}
