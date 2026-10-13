package com.lankapay.support.store;

import com.lankapay.support.domain.ConversationStatus;
import com.lankapay.support.domain.MessageSender;
import com.lankapay.support.domain.SupportConversation;
import com.lankapay.support.domain.SupportMessage;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Repository
public class InMemorySupportStore implements SupportStore {

    private final Map<String, SupportConversation> conversations = new ConcurrentHashMap<>();
    private final Map<String, List<SupportMessage>> conversationMessages = new ConcurrentHashMap<>();

    @Override
    public SupportConversation saveConversation(SupportConversation conversation) {
        conversations.put(conversation.id(), conversation);
        return conversation;
    }

    @Override
    public Optional<SupportConversation> findConversationById(String id) {
        return Optional.ofNullable(conversations.get(id));
    }

    @Override
    public List<SupportConversation> findConversationsByUserId(Long userId) {
        return conversations.values().stream()
                .filter(c -> Objects.equals(c.userId(), userId))
                .sorted(Comparator.comparing(SupportConversation::updatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<SupportConversation> findAllConversations(ConversationStatus status, String search) {
        return conversations.values().stream()
                .filter(c -> status == null || c.status() == status)
                .filter(c -> {
                    if (search == null || search.isBlank()) return true;
                    String s = search.toLowerCase();
                    boolean subjectMatch = c.subject() != null && c.subject().toLowerCase().contains(s);
                    boolean categoryMatch = c.category() != null && c.category().name().toLowerCase().contains(s);
                    boolean idMatch = c.id().toLowerCase().contains(s);
                    boolean userMatch = c.userId() != null && String.valueOf(c.userId()).contains(s);
                    return subjectMatch || categoryMatch || idMatch || userMatch;
                })
                .sorted(Comparator.comparing(SupportConversation::updatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public SupportMessage saveMessage(SupportMessage message) {
        conversationMessages.computeIfAbsent(message.conversationId(), k -> new CopyOnWriteArrayList<>())
                .add(message);

        // Update conversation's updatedAt
        SupportConversation conv = conversations.get(message.conversationId());
        if (conv != null) {
            conversations.put(conv.id(), new SupportConversation(
                    conv.id(), conv.userId(), conv.status(), conv.category(),
                    conv.subject(), conv.attachedTransactionRef(), conv.createdAt(), message.createdAt()
            ));
        }

        return message;
    }

    @Override
    public List<SupportMessage> findMessagesByConversationId(String conversationId, Instant after) {
        List<SupportMessage> msgs = conversationMessages.getOrDefault(conversationId, Collections.emptyList());
        if (after == null) {
            return new ArrayList<>(msgs);
        }
        return msgs.stream()
                .filter(m -> m.createdAt().isAfter(after))
                .collect(Collectors.toList());
    }

    @Override
    public void markMessagesRead(String conversationId, MessageSender senderNotEqualTo, Instant readAt) {
        List<SupportMessage> msgs = conversationMessages.get(conversationId);
        if (msgs == null) return;

        for (int i = 0; i < msgs.size(); i++) {
            SupportMessage m = msgs.get(i);
            if (m.sender() != senderNotEqualTo && m.readAt() == null) {
                msgs.set(i, m.withReadAt(readAt));
            }
        }
    }

    @Override
    public long countUnreadMessagesForUser(Long userId) {
        List<SupportConversation> userConvs = findConversationsByUserId(userId);
        long unread = 0;
        for (SupportConversation conv : userConvs) {
            List<SupportMessage> msgs = conversationMessages.getOrDefault(conv.id(), Collections.emptyList());
            unread += msgs.stream()
                    .filter(m -> m.sender() != MessageSender.USER && m.readAt() == null)
                    .count();
        }
        return unread;
    }
}
