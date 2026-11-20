package com.lankapay.insights.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.common.web.GlobalExceptionHandler;
import com.lankapay.insights.domain.InsightItem;
import com.lankapay.insights.domain.InsightType;
import com.lankapay.insights.service.InsightService;
import com.lankapay.insights.web.dto.DismissInsightRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InsightControllerTest {

    private MockMvc mockMvc;
    private InsightService insightService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        insightService = Mockito.mock(InsightService.class);
        InsightController controller = new InsightController(insightService, null);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void testGetInsights() throws Exception {
        InsightItem item = new InsightItem(
                "ins_1",
                InsightType.SPENDING_CHANGE,
                "Spending Increased",
                "Your spending is 10% higher than last month.",
                "/analytics"
        );
        when(insightService.getInsights(1L)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/insights?userId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("ins_1"))
                .andExpect(jsonPath("$[0].type").value("SPENDING_CHANGE"));
    }

    @Test
    void testDismissInsightPost() throws Exception {
        DismissInsightRequest req = new DismissInsightRequest(InsightType.BUDGET_LIMIT);

        mockMvc.perform(post("/api/v1/insights/dismiss?userId=1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"))
                .andExpect(jsonPath("$.type").value("BUDGET_LIMIT"));

        verify(insightService).dismissType(1L, InsightType.BUDGET_LIMIT);
    }

    @Test
    void testGetDismissedTypes() throws Exception {
        when(insightService.getDismissedTypes(1L)).thenReturn(Set.of(InsightType.EXPIRING_REWARDS));

        mockMvc.perform(get("/api/v1/insights/dismissed?userId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("EXPIRING_REWARDS"));
    }
}
