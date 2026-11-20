package com.lankapay.assistant.domain;

/**
 * Storage port interface for user consent to use AI assistant features.
 * Privacy Invariant: AI assistant is disabled by default until explicit consent is recorded.
 */
public interface AssistantConsentStore {

    boolean hasConsent(Long userId);

    void setConsent(Long userId, boolean consented);

    void revokeConsent(Long userId);
}
