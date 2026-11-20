package com.lankapay.assistant.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.assistant.service.AssistantService;
import com.lankapay.assistant.web.dto.*;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssistantControllerTest {

    private MockMvc mockMvc;
    private AssistantService assistantService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        assistantService = Mockito.mock(AssistantService.class);
        AssistantController controller = new AssistantController(assistantService, null);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void testGetConsent() throws Exception {
        when(assistantService.getConsent(1L)).thenReturn(new AssistantConsentResponse(1L, false));

        mockMvc.perform(get("/api/v1/assistant/consent?userId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consented").value(false));
    }

    @Test
    void testUpdateConsent() throws Exception {
        when(assistantService.updateConsent(1L, true)).thenReturn(new AssistantConsentResponse(1L, true));

        AssistantConsentRequest req = new AssistantConsentRequest(true);

        mockMvc.perform(post("/api/v1/assistant/consent?userId=1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consented").value(true));
    }

    @Test
    void testAskWhenNoConsentReturnsForbidden() throws Exception {
        when(assistantService.ask(eq(1L), any())).thenThrow(
                new AuthorizationException("CONSENT_REQUIRED", "Explicit user consent is required before querying.")
        );

        AssistantQueryRequest req = new AssistantQueryRequest("How do I pay bills?");

        mockMvc.perform(post("/api/v1/assistant/ask?userId=1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAskWhenConsentedReturnsSuccessWithDisclaimer() throws Exception {
        AssistantQueryResponse resp = new AssistantQueryResponse(
                "How do I pay bills?",
                "You can pay bills in the Pay Bills tab.",
                "AI-generated, may be wrong",
                List.of("/pay/bills"),
                Instant.now()
        );
        when(assistantService.ask(eq(1L), any())).thenReturn(resp);

        AssistantQueryRequest req = new AssistantQueryRequest("How do I pay bills?");

        mockMvc.perform(post("/api/v1/assistant/ask?userId=1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("You can pay bills in the Pay Bills tab."))
                .andExpect(jsonPath("$.disclaimer").value("AI-generated, may be wrong"))
                .andExpect(jsonPath("$.suggestedActions[0]").value("/pay/bills"));
    }
}
