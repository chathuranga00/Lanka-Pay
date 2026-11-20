package com.lankapay.support.domain;

import java.time.Instant;

public record SupportMessage(
        String id,
        String conversationId,
        MessageSender sender,
        String text,
        Instant createdAt,
        Instant readAt,
        String attachedTransactionRef
) {
    public SupportMessage withReadAt(Instant readTime) {
        return new SupportMessage(id, conversationId, sender, text, createdAt, readTime, attachedTransactionRef);
    }
}
