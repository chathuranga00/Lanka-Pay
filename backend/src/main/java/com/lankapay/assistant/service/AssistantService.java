package com.lankapay.assistant.service;

import com.lankapay.assistant.client.LlmClient;
import com.lankapay.assistant.domain.AssistantConsentStore;
import com.lankapay.assistant.web.dto.AssistantConsentResponse;
import com.lankapay.assistant.web.dto.AssistantQueryRequest;
import com.lankapay.assistant.web.dto.AssistantQueryResponse;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.insights.domain.InsightItem;
import com.lankapay.insights.service.InsightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-side AI Assistant gateway service.
 * Enforces consent, privacy redaction, rate limiting, and output labelling.
 *
 * Privacy Invariants:
 * 1. Disabled by default; explicit consent required.
 * 2. Prompts and replies are NEVER logged to loggers or persistent storage.
 * 3. Never accepts or forwards support chat messages.
 * 4. PII, card numbers, accounts, and exact amounts are stripped before prompt creation.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final String AI_DISCLAIMER = "AI-generated, may be wrong";

    private final AssistantConsentStore consentStore;
    private final AssistantDataSanitizer dataSanitizer;
    private final AssistantRateLimiter rateLimiter;
    private final LlmClient llmClient;
    private final InsightService insightService;

    public AssistantService(
            AssistantConsentStore consentStore,
            AssistantDataSanitizer dataSanitizer,
            AssistantRateLimiter rateLimiter,
            LlmClient llmClient,
            InsightService insightService) {
        this.consentStore = consentStore;
        this.dataSanitizer = dataSanitizer;
        this.rateLimiter = rateLimiter;
        this.llmClient = llmClient;
        this.insightService = insightService;
    }

    public AssistantConsentResponse getConsent(Long userId) {
        boolean consented = consentStore.hasConsent(userId);
        return new AssistantConsentResponse(userId, consented);
    }

    public AssistantConsentResponse updateConsent(Long userId, boolean consented) {
        consentStore.setConsent(userId, consented);
        log.info("AI assistant consent updated for userId={} consented={}", userId, consented);
        return new AssistantConsentResponse(userId, consented);
    }

    public AssistantQueryResponse ask(Long userId, AssistantQueryRequest request) {
        if (userId == null) {
            userId = 1L;
        }

        // 1. Consent check
        if (!consentStore.hasConsent(userId)) {
            throw new AuthorizationException("CONSENT_REQUIRED",
                    "AI assistant is disabled by default. Explicit user consent is required before querying the assistant.");
        }

        // 2. Rate limit check
        rateLimiter.checkLimit(userId);

        // 3. Privacy Sanitize User Prompt (strips cards, accounts, phones, OTPs, exact amounts)
        String rawQuery = request != null ? request.query() : "";
        String sanitizedQuery = dataSanitizer.sanitizeUserPrompt(rawQuery);

        // 4. Build System Prompt with aggregated insights only (no PII, no raw transactions, no support chat)
        String systemPrompt = buildSystemPrompt(userId);

        // 5. Call LLM provider (Mock / external) without logging prompts or replies
        String rawAnswer = llmClient.complete(systemPrompt, sanitizedQuery);

        // 6. Suggest relevant deep-link navigation actions based on topic
        List<String> suggestedActions = resolveSuggestedActions(sanitizedQuery);

        // Privacy Invariant: Do NOT log the sanitizedQuery or rawAnswer!
        log.debug("AI assistant successfully answered query for userId={}", userId);

        return new AssistantQueryResponse(
                sanitizedQuery,
                rawAnswer,
                AI_DISCLAIMER,
                suggestedActions,
                Instant.now()
        );
    }

    private String buildSystemPrompt(Long userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the LankaPay SuperApp AI Assistant.\n");
        sb.append("Your role is strictly to explain app features and summarize user aggregated insights.\n");
        sb.append("Guardrails:\n");
        sb.append("1. Never give investment, tax, or legal financial advice.\n");
        sb.append("2. You cannot move money, make transfers, or process payments.\n");
        sb.append("3. You cannot change settings, passwords, or PINs.\n");

        if (insightService != null) {
            try {
                List<InsightItem> insights = insightService.getInsights(userId);
                if (insights != null && !insights.isEmpty()) {
                    sb.append("\nUSER_INSIGHTS:\n");
                    for (InsightItem item : insights) {
                        sb.append("- ").append(item.title()).append(": ").append(item.text()).append("\n");
                    }
                }
            } catch (Exception ex) {
                log.debug("Insights resolution for assistant prompt bypassed: {}", ex.getMessage());
            }
        }

        return sb.toString();
    }

    private List<String> resolveSuggestedActions(String sanitizedQuery) {
        List<String> actions = new ArrayList<>();
        String lower = sanitizedQuery.toLowerCase();
        if (lower.contains("send") || lower.contains("transfer")) {
            actions.add("/pay/send");
        }
        if (lower.contains("qr") || lower.contains("scan")) {
            actions.add("/pay/qr");
        }
        if (lower.contains("bill") || lower.contains("utility")) {
            actions.add("/pay/bills");
        }
        if (lower.contains("split")) {
            actions.add("/pay/split");
        }
        if (lower.contains("card")) {
            actions.add("/wallet/cards");
        }
        if (lower.contains("reward") || lower.contains("point")) {
            actions.add("/rewards");
        }
        if (lower.contains("insight") || lower.contains("spend") || lower.contains("budget")) {
            actions.add("/analytics");
        }
        return actions;
    }
}
