package com.lankapay.goals.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lankapay.common.web.GlobalExceptionHandler;
import com.lankapay.goals.domain.GoalLedgerEntryType;
import com.lankapay.goals.domain.GoalStatus;
import com.lankapay.goals.service.GoalService;
import com.lankapay.goals.web.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GoalControllerTest {

    private MockMvc mockMvc;
    private GoalService goalService;
    private ObjectMapper objectMapper;

    private static final Long TEST_USER_ID = 42L;

    @BeforeEach
    void setUp() {
        goalService = mock(GoalService.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        HandlerMethodArgumentResolver authResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                String authHeader = webRequest.getHeader("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    return null;
                }
                return TEST_USER_ID;
            }
        };

        GoalController controller = new GoalController(goalService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(authResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/goals - unauthenticated request returns 401")
    void listGoalsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/goals"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/goals - authenticated returns list of user goals")
    void listGoalsAuthenticated() throws Exception {
        GoalDto goal = new GoalDto(
                "g-1", "MacBook", 300_000L, 100_000L,
                LocalDate.now().plusMonths(2), "ACC-101",
                GoalStatus.ACTIVE, "SIMULATED", 33.33,
                Instant.now(), Instant.now()
        );
        when(goalService.listGoals(TEST_USER_ID)).thenReturn(List.of(goal));

        mockMvc.perform(get("/api/v1/goals")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("MacBook"))
                .andExpect(jsonPath("$[0].ledgerType").value("SIMULATED"))
                .andExpect(jsonPath("$[0].savedMinor").value(100_000));
    }

    @Test
    @DisplayName("POST /api/v1/goals - create new goal succeeds with 201 Created")
    void createGoalSuccess() throws Exception {
        CreateGoalRequest req = new CreateGoalRequest(
                "Trip to Sigiriya", 50_000L, LocalDate.now().plusMonths(1), "ACC-101", 0L, null);

        GoalDto created = new GoalDto(
                "g-2", "Trip to Sigiriya", 50_000L, 0L,
                LocalDate.now().plusMonths(1), "ACC-101",
                GoalStatus.ACTIVE, "SIMULATED", 0.0,
                Instant.now(), Instant.now()
        );

        when(goalService.createGoal(eq(TEST_USER_ID), any(), any(), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/goals")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("g-2"))
                .andExpect(jsonPath("$.ledgerType").value("SIMULATED"));
    }

    @Test
    @DisplayName("POST /api/v1/goals/{id}/contribute - succeeds with Idempotency-Key and step-up auth")
    void contributeSuccess() throws Exception {
        GoalTransactionRequest req = new GoalTransactionRequest(25_000L, "Savings", "123456");

        GoalDto updatedGoal = new GoalDto(
                "g-1", "MacBook", 300_000L, 125_000L,
                LocalDate.now().plusMonths(2), "ACC-101",
                GoalStatus.ACTIVE, "SIMULATED", 41.67,
                Instant.now(), Instant.now()
        );
        GoalLedgerEntryDto entry = new GoalLedgerEntryDto(
                "e-1", "g-1", GoalLedgerEntryType.CONTRIBUTION,
                25_000L, 125_000L, "IDEMP-99", "Savings",
                Instant.now(), "SIMULATED"
        );
        GoalTransactionResponse response = GoalTransactionResponse.success(
                updatedGoal, entry, "Contribution recorded successfully in simulated ledger");

        when(goalService.contribute(eq(TEST_USER_ID), eq("g-1"), any(), eq("IDEMP-99"), eq("123456"), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/goals/g-1/contribute")
                        .header("Authorization", "Bearer valid-token")
                        .header("Idempotency-Key", "IDEMP-99")
                        .header("X-Step-Up-Pin", "123456")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.ledgerType").value("SIMULATED"))
                .andExpect(jsonPath("$.goal.savedMinor").value(125_000));
    }

    @Test
    @DisplayName("POST /api/v1/goals/{id}/withdraw - succeeds with Idempotency-Key and step-up auth")
    void withdrawSuccess() throws Exception {
        GoalTransactionRequest req = new GoalTransactionRequest(10_000L, "Emergency", "123456");

        GoalDto updatedGoal = new GoalDto(
                "g-1", "MacBook", 300_000L, 90_000L,
                LocalDate.now().plusMonths(2), "ACC-101",
                GoalStatus.ACTIVE, "SIMULATED", 30.0,
                Instant.now(), Instant.now()
        );
        GoalLedgerEntryDto entry = new GoalLedgerEntryDto(
                "e-2", "g-1", GoalLedgerEntryType.WITHDRAWAL,
                10_000L, 90_000L, "IDEMP-100", "Emergency",
                Instant.now(), "SIMULATED"
        );
        GoalTransactionResponse response = GoalTransactionResponse.success(
                updatedGoal, entry, "Withdrawal recorded successfully in simulated ledger");

        when(goalService.withdraw(eq(TEST_USER_ID), eq("g-1"), any(), eq("IDEMP-100"), eq("123456"), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/goals/g-1/withdraw")
                        .header("Authorization", "Bearer valid-token")
                        .header("Idempotency-Key", "IDEMP-100")
                        .header("X-Step-Up-Pin", "123456")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.ledgerType").value("SIMULATED"))
                .andExpect(jsonPath("$.goal.savedMinor").value(90_000));
    }
}
