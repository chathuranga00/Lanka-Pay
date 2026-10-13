package com.lankapay.support.web.dto;

import com.lankapay.support.domain.ConversationStatus;
import com.lankapay.support.domain.SupportCategory;
import com.lankapay.support.domain.SupportConversation;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;

public record SupportConversationDto(
        String id,
        Long userId,
        ConversationStatus status,
        SupportCategory category,
        String subject,
        String attachedTransactionRef,
        Instant createdAt,
        Instant updatedAt,
        long unreadCount,
        SupportMessageDto lastMessage
) {
    public static SupportConversationDto fromDomain(SupportConversation c, long unreadCount, SupportMessageDto lastMessage) {
        String safeSubject = c.subject() != null ? HtmlUtils.htmlEscape(c.subject()) : "";
        return new SupportConversationDto(
                c.id(),
                c.userId(),
                c.status(),
                c.category(),
                safeSubject,
                c.attachedTransactionRef(),
                c.createdAt(),
                c.updatedAt(),
                unreadCount,
                lastMessage
        );
    }
}
