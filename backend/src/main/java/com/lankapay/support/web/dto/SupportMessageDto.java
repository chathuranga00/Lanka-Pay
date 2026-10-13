package com.lankapay.support.web.dto;

import com.lankapay.support.domain.MessageSender;
import com.lankapay.support.domain.SupportMessage;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;

public record SupportMessageDto(
        String id,
        String conversationId,
        MessageSender sender,
        String text,
        Instant createdAt,
        Instant readAt,
        String attachedTransactionRef
) {
    public static SupportMessageDto fromDomain(SupportMessage msg) {
        String safeText = msg.text() != null ? HtmlUtils.htmlEscape(msg.text()) : "";
        return new SupportMessageDto(
                msg.id(),
                msg.conversationId(),
                msg.sender(),
                safeText,
                msg.createdAt(),
                msg.readAt(),
                msg.attachedTransactionRef()
        );
    }
}
