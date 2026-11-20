package com.lankapay.support.domain;

import java.time.Instant;

public record SupportConversation(
        String id,
        Long userId,
        ConversationStatus status,
        SupportCategory category,
        String subject,
        String attachedTransactionRef,
        Instant createdAt,
        Instant updatedAt
) {
    public SupportConversation withStatus(ConversationStatus newStatus) {
        return new SupportConversation(
                id, userId, newStatus, category, subject, attachedTransactionRef, createdAt, Instant.now()
        );
    }

    public SupportConversation withAttachedTransactionRef(String txRef) {
        return new SupportConversation(
                id, userId, status, category, subject, txRef, createdAt, Instant.now()
        );
    }
}
