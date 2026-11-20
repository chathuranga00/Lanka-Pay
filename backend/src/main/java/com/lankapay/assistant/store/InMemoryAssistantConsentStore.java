package com.lankapay.assistant.store;

import com.lankapay.assistant.domain.AssistantConsentStore;
import org.springframework.stereotype.Repository;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory store for AI assistant user consent.
 * Default is FALSE (disabled) for all users.
 */
@Repository
public class InMemoryAssistantConsentStore implements AssistantConsentStore {

    private final ConcurrentHashMap<Long, Boolean> userConsents = new ConcurrentHashMap<>();

    @Override
    public boolean hasConsent(Long userId) {
        if (userId == null) {
            return false;
        }
        return userConsents.getOrDefault(userId, false);
    }

    @Override
    public void setConsent(Long userId, boolean consented) {
        if (userId != null) {
            userConsents.put(userId, consented);
        }
    }

    @Override
    public void revokeConsent(Long userId) {
        if (userId != null) {
            userConsents.put(userId, false);
        }
    }
}
