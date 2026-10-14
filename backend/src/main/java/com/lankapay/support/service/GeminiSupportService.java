package com.lankapay.support.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.support.config.GeminiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * Intelligent AI Customer Support Assistant powered by Google Gemini.
 * Features:
 * - Domain-grounded system instructions for LankaPay instant payments, proxy IDs, LankaQR, and bill settlements.
 * - Strict banking security boundaries (never collects or echoes PINs, OTPs, CVVs, or card numbers).
 * - Automatic escalation tagging ([ESCALATE_TO_AGENT]) to transfer inquiries to human agents in the Admin Portal.
 * - Graceful fallback to rule-based FaqBotEngine if Gemini API is unreachable or key is unset.
 */
@Service
public class GeminiSupportService {

    private static final Logger log = LoggerFactory.getLogger(GeminiSupportService.class);
    public static final String ESCALATION_TAG = "[ESCALATE_TO_AGENT]";

    private final GeminiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public record GeminiAnswer(String text, boolean transferToAgent, String model) {}

    public GeminiSupportService(GeminiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(12000);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public boolean isAvailable() {
        return properties.isEnabled()
                && properties.getApiKey() != null
                && !properties.getApiKey().isBlank();
    }

    /**
     * Answers a customer's inquiry using Google Gemini AI.
     *
     * @param userMessage Sanitized customer question
     * @param contextOptional Optional context such as attached transaction reference
     * @return GeminiAnswer containing generated text and escalation indicator, or Optional.empty() on error/fallback.
     */
    public Optional<GeminiAnswer> askGemini(String userMessage, String contextOptional) {
        if (!isAvailable() || userMessage == null || userMessage.isBlank()) {
            return Optional.empty();
        }

        try {
            String maskedQuery = DataMaskingFilter.mask(userMessage);

            Map<String, Object> requestBody = new LinkedHashMap<>();

            // System instruction
            Map<String, Object> systemInstruction = Map.of(
                    "parts", List.of(Map.of("text", buildSystemPrompt(contextOptional)))
            );
            requestBody.put("system_instruction", systemInstruction);

            // Contents
            Map<String, Object> userContent = Map.of(
                    "role", "user",
                    "parts", List.of(Map.of("text", maskedQuery))
            );
            requestBody.put("contents", List.of(userContent));

            // Generation config
            Map<String, Object> genConfig = Map.of(
                    "temperature", properties.getTemperature(),
                    "maxOutputTokens", properties.getMaxTokens()
            );
            requestBody.put("generationConfig", genConfig);

            String url = String.format("%s/models/%s:generateContent?key=%s",
                    properties.getEndpoint(),
                    properties.getModel(),
                    properties.getApiKey()
            );

            String responseJson = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (responseJson == null || responseJson.isBlank()) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                return Optional.empty();
            }

            JsonNode firstCandidate = candidates.get(0);
            JsonNode parts = firstCandidate.path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                return Optional.empty();
            }

            StringBuilder textBuilder = new StringBuilder();
            for (JsonNode part : parts) {
                if (part.has("text")) {
                    textBuilder.append(part.get("text").asText());
                }
            }

            String rawAnswer = textBuilder.toString().trim();
            if (rawAnswer.isEmpty()) {
                return Optional.empty();
            }

            boolean transferToAgent = rawAnswer.contains(ESCALATION_TAG)
                    || rawAnswer.toLowerCase().contains("connecting you with a support agent")
                    || rawAnswer.toLowerCase().contains("transferred to a human agent");

            // Strip the internal escalation tag from customer view
            String cleanAnswer = rawAnswer.replace(ESCALATION_TAG, "").trim();

            return Optional.of(new GeminiAnswer(cleanAnswer, transferToAgent, properties.getModel()));

        } catch (Exception ex) {
            log.warn("Gemini AI support assistant invocation failed: {}. Falling back to rule-based engine.", ex.getMessage());
            return Optional.empty();
        }
    }

    private String buildSystemPrompt(String contextOptional) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the official AI Customer Support Assistant for the LankaPay Digital Payments Super App in Sri Lanka.\n");
        sb.append("Your role is to assist users politely, accurately, and concisely with digital banking and payment inquiries.\n\n");

        sb.append("DOMAIN KNOWLEDGE:\n");
        sb.append("- LankaPay ID / Proxy Registry: Allows users to link their mobile phone number or National Identity Card (NIC) to their bank account for alias-based transfers without sharing account numbers.\n");
        sb.append("- LankaQR: National QR payment standard for instant retail and P2P payments in Sri Lanka.\n");
        sb.append("- Transaction States: PENDING or UNCERTAIN states occur when network reconciliation with the beneficiary bank is in progress. Automated reconciliation settles status within minutes or initiates auto-reversal.\n");
        sb.append("- Security Controls: Users can freeze/unfreeze cards immediately inside the app.\n");

        if (contextOptional != null && !contextOptional.isBlank()) {
            sb.append("\nATTACHED TRANSACTION REFERENCE: ").append(contextOptional).append("\n");
        }

        sb.append("\nCRITICAL SECURITY RULES:\n");
        sb.append("1. NEVER ask for, accept, or repeat user PINs, passwords, OTPs, CVVs, or full card numbers.\n");
        sb.append("2. Always emphasize that LankaPay employees and automated bots will NEVER request these credentials.\n\n");

        sb.append("ESCALATION POLICY:\n");
        sb.append("If the customer explicitly asks to talk to a human agent, operator, or representative, OR reports unauthorized account activity, stolen card, or complex unresolved dispute, respond with warm reassurance that an agent is taking over and append the tag '").append(ESCALATION_TAG).append("' at the very end of your response.\n");

        sb.append("\nTone: Professional, helpful, concise, reassuring. Format with clean bullet points when explaining multi-step guides.");
        return sb.toString();
    }
}
