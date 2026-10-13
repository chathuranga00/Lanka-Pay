package com.lankapay.support.store;

import com.lankapay.support.domain.ConversationStatus;
import com.lankapay.support.domain.MessageSender;
import com.lankapay.support.domain.SupportConversation;
import com.lankapay.support.domain.SupportMessage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SupportStore {

    SupportConversation saveConversation(SupportConversation conversation);

    Optional<SupportConversation> findConversationById(String id);

    List<SupportConversation> findConversationsByUserId(Long userId);

    List<SupportConversation> findAllConversations(ConversationStatus status, String search);

    SupportMessage saveMessage(SupportMessage message);

    List<SupportMessage> findMessagesByConversationId(String conversationId, Instant after);

    void markMessagesRead(String conversationId, MessageSender senderNotEqualTo, Instant readAt);

    long countUnreadMessagesForUser(Long userId);
}
