package com.lankapay.assistant;

import com.lankapay.assistant.client.MockLlmClient;
import com.lankapay.assistant.domain.AssistantConsentStore;
import com.lankapay.assistant.service.AssistantDataSanitizer;
import com.lankapay.assistant.service.AssistantRateLimiter;
import com.lankapay.assistant.service.AssistantService;
import com.lankapay.assistant.store.InMemoryAssistantConsentStore;
import com.lankapay.assistant.web.dto.AssistantQueryRequest;
import com.lankapay.assistant.web.dto.AssistantQueryResponse;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.RateLimitException;
import com.lankapay.insights.domain.InsightItem;
import com.lankapay.insights.domain.InsightType;
import com.lankapay.insights.service.InsightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AssistantModuleTest {

    private AssistantConsentStore consentStore;
    private AssistantDataSanitizer dataSanitizer;
    private AssistantRateLimiter rateLimiter;
    private MockLlmClient llmClient;
    private InsightService insightService;
    private AssistantService assistantService;

    @BeforeEach
    void setUp() {
        consentStore = new InMemoryAssistantConsentStore();
        dataSanitizer = new AssistantDataSanitizer();
        rateLimiter = new AssistantRateLimiter(5); // 5 per min for test
        llmClient = new MockLlmClient();
        insightService = Mockito.mock(InsightService.class);

        assistantService = new AssistantService(
                consentStore,
                dataSanitizer,
                rateLimiter,
                llmClient,
                insightService
        );
    }

    @Test
    @DisplayName("Redaction test: cards, account numbers, phones, OTPs, and exact amounts are stripped")
    void testSanitizationStripsCardsAccountsPhonesOtpsAndExactAmounts() {
        String input = "My card is 4111-2222-3333-4444 and my OTP is 123456. "
                + "Call me on +94771234567 regarding my account LK12CEYB0001234567890123. "
                + "I paid LKR 12,500.50 on transaction TXN-99887766 and password: mySecretPassword123.";

        String sanitized = dataSanitizer.sanitizeUserPrompt(input);

        // Card masked
        assertThat(sanitized).doesNotContain("4111-2222-3333-4444");
        assertThat(sanitized).contains("[CARD_MASKED]");

        // OTP masked
        assertThat(sanitized).doesNotContain("123456");
        assertThat(sanitized).contains("[OTP_MASKED]");

        // Phone masked
        assertThat(sanitized).doesNotContain("+94771234567");
        assertThat(sanitized).contains("[PHONE_MASKED]");

        // Account masked
        assertThat(sanitized).doesNotContain("LK12CEYB0001234567890123");
        assertThat(sanitized).contains("[ACCOUNT_MASKED]");

        // Exact amount masked
        assertThat(sanitized).doesNotContain("12,500.50");
        assertThat(sanitized).contains("[AMOUNT]");

        // Ref masked
        assertThat(sanitized).doesNotContain("TXN-99887766");
        assertThat(sanitized).contains("[REF_MASKED]");

        // Secret masked
        assertThat(sanitized).doesNotContain("mySecretPassword123");
        assertThat(sanitized).contains("[SECRET_MASKED]");
    }

    @Test
    @DisplayName("Privacy: assistant is disabled by default; throws AuthorizationException if no consent")
    void testConsentRequiredBeforeQuerying() {
        Long userId = 101L;
        assertThat(consentStore.hasConsent(userId)).isFalse();

        AssistantQueryRequest req = new AssistantQueryRequest("How do I send money?");

        assertThatThrownBy(() -> assistantService.ask(userId, req))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("Explicit user consent is required");
    }

    @Test
    @DisplayName("Consent granted allows querying and answers are labeled 'AI-generated, may be wrong'")
    void testConsentGrantedAllowsQueryingWithDisclaimer() {
        Long userId = 102L;
        assistantService.updateConsent(userId, true);
        assertThat(assistantService.getConsent(userId).consented()).isTrue();

        AssistantQueryRequest req = new AssistantQueryRequest("How do I send money?");
        AssistantQueryResponse resp = assistantService.ask(userId, req);

        assertThat(resp).isNotNull();
        assertThat(resp.answer()).contains("Send");
        assertThat(resp.disclaimer()).isEqualTo("AI-generated, may be wrong");
        assertThat(resp.suggestedActions()).contains("/pay/send");
    }

    @Test
    @DisplayName("Rate limiting: exceeding query capacity throws RateLimitException")
    void testRateLimitExceededThrowsException() {
        Long userId = 103L;
        assistantService.updateConsent(userId, true);

        AssistantQueryRequest req = new AssistantQueryRequest("How does QR payment work?");

        // Consume 5 queries (the configured capacity)
        for (int i = 0; i < 5; i++) {
            assistantService.ask(userId, req);
        }

        // 6th query should be blocked
        assertThatThrownBy(() -> assistantService.ask(userId, req))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("Guardrail: Refusal of money movements")
    void testRefusalOfMoneyActions() {
        Long userId = 104L;
        assistantService.updateConsent(userId, true);

        AssistantQueryRequest req = new AssistantQueryRequest("Send money to John please, LKR 5,000");
        AssistantQueryResponse resp = assistantService.ask(userId, req);

        assertThat(resp.answer()).contains("cannot move money");
        assertThat(resp.disclaimer()).isEqualTo("AI-generated, may be wrong");
    }

    @Test
    @DisplayName("Guardrail: Refusal of security/settings changes")
    void testRefusalOfSettingsChanges() {
        Long userId = 105L;
        assistantService.updateConsent(userId, true);

        AssistantQueryRequest req = new AssistantQueryRequest("Change my PIN to 9999");
        AssistantQueryResponse resp = assistantService.ask(userId, req);

        assertThat(resp.answer()).contains("cannot change account settings");
    }

    @Test
    @DisplayName("Guardrail: Refusal of investment/tax/financial advice")
    void testRefusalOfInvestmentAdvice() {
        Long userId = 106L;
        assistantService.updateConsent(userId, true);

        AssistantQueryRequest req = new AssistantQueryRequest("What stock should I invest in this week?");
        AssistantQueryResponse resp = assistantService.ask(userId, req);

        assertThat(resp.answer()).contains("cannot provide investment, tax, or legal financial advice");
    }

    @Test
    @DisplayName("Summarize aggregated insights from user data")
    void testSummarizeAggregatedInsights() {
        Long userId = 107L;
        assistantService.updateConsent(userId, true);

        InsightItem item = new InsightItem(
                "ins_1",
                InsightType.SPENDING_CHANGE,
                "Spending Increased",
                "Your spending this month is 12.5% higher than last month.",
                "/analytics"
        );
        when(insightService.getInsights(eq(userId))).thenReturn(List.of(item));

        AssistantQueryRequest req = new AssistantQueryRequest("Can you give me an overview of my insights?");
        AssistantQueryResponse resp = assistantService.ask(userId, req);

        assertThat(resp.answer()).contains("Spending Increased");
        assertThat(resp.answer()).contains("12.5% higher");
    }
}
