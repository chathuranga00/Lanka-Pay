package com.lankapay.family.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lankapay.common.web.GlobalExceptionHandler;
import com.lankapay.family.domain.AgeBand;
import com.lankapay.family.domain.FamilyMemberStatus;
import com.lankapay.family.domain.FamilyTransactionStatus;
import com.lankapay.family.domain.FamilyTransactionType;
import com.lankapay.family.service.FamilyService;
import com.lankapay.family.web.dto.*;
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
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FamilyControllerTest {

    private MockMvc mockMvc;
    private FamilyService familyService;
    private ObjectMapper objectMapper;

    private static final Long TEST_USER_ID = 42L;

    @BeforeEach
    void setUp() {
        familyService = mock(FamilyService.class);
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

        FamilyController controller = new FamilyController(familyService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(authResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/family/members without auth returns 401")
    void testListMembers_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/family/members"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/family/members with auth returns 200 and list")
    void testListMembers_Success() throws Exception {
        FamilyMemberDto member = new FamilyMemberDto(
                "mem-1",
                TEST_USER_ID,
                "Kasun",
                AgeBand.AGE_10_TO_13,
                "KA",
                50_000L,
                200_000L,
                1_000_000L,
                Set.of("FOOD", "EDUCATION"),
                false,
                null,
                10_000L,
                25_000L,
                Instant.now(),
                Instant.now(),
                FamilyMemberStatus.ACTIVE
        );
        when(familyService.listMembers(TEST_USER_ID)).thenReturn(List.of(member));

        mockMvc.perform(get("/api/v1/family/members")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].firstName").value("Kasun"))
                .andExpect(jsonPath("$[0].avatarInitials").value("KA"));
    }

    @Test
    @DisplayName("POST /api/v1/family/members creates profile")
    void testCreateMember_Success() throws Exception {
        CreateFamilyMemberRequest req = new CreateFamilyMemberRequest(
                "Anura",
                AgeBand.UNDER_10,
                "AN",
                100_000L,
                500_000L,
                Set.of("FOOD"),
                null
        );

        FamilyMemberDto created = new FamilyMemberDto(
                "mem-2",
                TEST_USER_ID,
                "Anura",
                AgeBand.UNDER_10,
                "AN",
                0L,
                100_000L,
                500_000L,
                Set.of("FOOD"),
                false,
                null,
                0L,
                0L,
                Instant.now(),
                Instant.now(),
                FamilyMemberStatus.ACTIVE
        );
        when(familyService.createMember(eq(TEST_USER_ID), any(), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/family/members")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("mem-2"))
                .andExpect(jsonPath("$.firstName").value("Anura"));
    }

    @Test
    @DisplayName("PUT /api/v1/family/members/{id}/controls updates controls")
    void testUpdateControls() throws Exception {
        UpdateFamilyControlsRequest req = new UpdateFamilyControlsRequest(
                150_000L, 800_000L, Set.of("FOOD", "TRANSPORT"), false, null
        );

        FamilyMemberDto updated = new FamilyMemberDto(
                "mem-1",
                TEST_USER_ID,
                "Kasun",
                AgeBand.AGE_10_TO_13,
                "KA",
                50_000L,
                150_000L,
                800_000L,
                Set.of("FOOD", "TRANSPORT"),
                false,
                null,
                0L,
                0L,
                Instant.now(),
                Instant.now(),
                FamilyMemberStatus.ACTIVE
        );
        when(familyService.updateControls(eq(TEST_USER_ID), eq("mem-1"), any(), any())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/family/members/mem-1/controls")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyLimitMinor").value(150_000));
    }

    @Test
    @DisplayName("POST /api/v1/family/members/{id}/fund funds simulated balance")
    void testFundMember() throws Exception {
        FundFamilyMemberRequest req = new FundFamilyMemberRequest(200_000L, "Main Bank", "Allowance");

        FamilyMemberDto funded = new FamilyMemberDto(
                "mem-1",
                TEST_USER_ID,
                "Kasun",
                AgeBand.AGE_10_TO_13,
                "KA",
                250_000L,
                150_000L,
                800_000L,
                Set.of("FOOD"),
                false,
                null,
                0L,
                0L,
                Instant.now(),
                Instant.now(),
                FamilyMemberStatus.ACTIVE
        );
        when(familyService.fundMember(eq(TEST_USER_ID), eq("mem-1"), any(), any())).thenReturn(funded);

        mockMvc.perform(post("/api/v1/family/members/mem-1/fund")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceMinor").value(250_000));
    }

    @Test
    @DisplayName("POST /api/v1/family/members/{id}/spend executes spend")
    void testSpend() throws Exception {
        SpendFamilyMemberRequest req = new SpendFamilyMemberRequest(15_000L, "FOOD", "School Bakery", "Snack");

        FamilyTransactionDto tx = new FamilyTransactionDto(
                "tx-1",
                "mem-1",
                FamilyTransactionType.SPEND,
                15_000L,
                235_000L,
                "FOOD",
                "School Bakery",
                "Snack",
                FamilyTransactionStatus.APPROVED,
                null,
                Instant.now()
        );
        when(familyService.spend(eq("mem-1"), any(), any())).thenReturn(tx);

        mockMvc.perform(post("/api/v1/family/members/mem-1/spend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.amountMinor").value(15_000));
    }

    @Test
    @DisplayName("GET /api/v1/family/members/{id}/child-view returns minimal view")
    void testChildView() throws Exception {
        ChildViewDto childView = new ChildViewDto(
                "mem-1",
                "Kasun",
                "KA",
                235_000L,
                150_000L,
                135_000L,
                Set.of("FOOD"),
                false,
                List.of()
        );
        when(familyService.getChildView(eq(TEST_USER_ID), eq("mem-1"))).thenReturn(childView);

        mockMvc.perform(get("/api/v1/family/members/mem-1/child-view")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Kasun"))
                .andExpect(jsonPath("$.remainingDailyMinor").value(135_000));
    }

    @Test
    @DisplayName("DELETE /api/v1/family/members/{id} deletes profile")
    void testDeleteMember() throws Exception {
        mockMvc.perform(delete("/api/v1/family/members/mem-1")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNoContent());
    }
}
