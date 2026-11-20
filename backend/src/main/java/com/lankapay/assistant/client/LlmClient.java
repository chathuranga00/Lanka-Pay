package com.lankapay.assistant.client;

/**
 * Pluggable client interface for AI language models.
 * Decouples the assistant engine from specific LLM providers.
 * Invariant: All prompts passed to this interface must already be stripped of PII and secrets.
 * Invariant: Implementations must not log prompt or completion bodies to logs.
 */
public interface LlmClient {

    /**
     * Submit a completion request to the language model.
     *
     * @param systemPrompt system instructions and high-level grounded context
     * @param sanitizedUserPrompt sanitized user query (stripped of PII, secrets, exact amounts)
     * @return model answer
     */
    String complete(String systemPrompt, String sanitizedUserPrompt);
}
