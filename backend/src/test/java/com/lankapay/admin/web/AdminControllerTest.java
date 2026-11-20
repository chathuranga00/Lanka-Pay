package com.lankapay.admin.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.admin.domain.FraudAlertSeverity;
import com.lankapay.admin.domain.FraudAlertStatus;
import com.lankapay.admin.domain.FraudAlertType;
import com.lankapay.admin.service.*;
import com.lankapay.admin.web.dto.*;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.common.web.GlobalExceptionHandler;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.web.dto.ReconcileResultResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock AdminUserService adminUserService;
    @Mock AdminBankService adminBankService;
    @Mock AdminTransferService adminTransferService;
    @Mock RiskManagementService riskManagementService;
    @Mock AdminAuditService adminAuditService;

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    static final Long ADMIN_ID = 999L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        AdminController controller = new AdminController(
                adminUserService,
                adminBankService,
                adminTransferService,
                riskManagementService,
                adminAuditService
        );

        HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return ADMIN_ID;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(principalResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/admin/users returns paginated user summaries without passwords/tokens")
    void searchUsers() throws Exception {
        AdminUserSummaryResponse summary = new AdminUserSummaryResponse(
                1L, "Kamal P.", "+9477***4567", "1990****", UserStatus.ACTIVE, UserRole.CUSTOMER, Instant.now()
        );
        when(adminUserService.searchUsers(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Kamal P."))
                .andExpect(jsonPath("$.content[0].maskedPhone").value("+9477***4567"))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].otp").doesNotExist())
                .andExpect(jsonPath("$.content[0].token").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/admin/users/{id} returns user detail without secrets")
    void getUserDetail() throws Exception {
        AdminUserDetailResponse detail = new AdminUserDetailResponse(
                1L, "Kamal P.", "+9477***4567", "1990****", UserStatus.ACTIVE, UserRole.CUSTOMER,
                0, null, List.of(), Instant.now(), Instant.now()
        );
        when(adminUserService.getUserDetail(1L)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/admin/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/users/{id}/status updates user status")
    void updateUserStatus() throws Exception {
        AdminUserDetailResponse detail = new AdminUserDetailResponse(
                1L, "Kamal P.", "+9477***4567", "1990****", UserStatus.FROZEN, UserRole.CUSTOMER,
                0, null, List.of(), Instant.now(), Instant.now()
        );
        when(adminUserService.updateUserStatus(eq(1L), any(), eq(ADMIN_ID), any()))
                .thenReturn(detail);

        UpdateUserStatusRequest req = new UpdateUserStatusRequest(UserStatus.FROZEN, "Suspicious activity");

        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/banks returns bank configs")
    void getBanks() throws Exception {
        BankConfigResponse bank = new BankConfigResponse(
                1L, "SIMULATED_BANK_A", "Bank A [SIMULATED]", true, true, Instant.now()
        );
        when(adminBankService.getAllBanks()).thenReturn(List.of(bank));

        mockMvc.perform(get("/api/v1/admin/banks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bankCode").value("SIMULATED_BANK_A"))
                .andExpect(jsonPath("$[0].simulated").value(true));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/banks/{bankCode} updates bank config")
    void updateBankConfig() throws Exception {
        BankConfigResponse bank = new BankConfigResponse(
                1L, "SIMULATED_BANK_A", "Bank A Updated", true, false, Instant.now()
        );
        when(adminBankService.updateBankConfig(eq("SIMULATED_BANK_A"), any(), eq(ADMIN_ID), any()))
                .thenReturn(bank);

        UpdateBankConfigRequest req = new UpdateBankConfigRequest("Bank A Updated", false);

        mockMvc.perform(patch("/api/v1/admin/banks/SIMULATED_BANK_A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Bank A Updated"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/admin/integrations/health returns integration health")
    void getIntegrationHealth() throws Exception {
        IntegrationHealthResponse health = new IntegrationHealthResponse(
                "UP", Instant.now(), List.of(new ComponentHealthResponse("Bank A", "UP", true, 2L, "OK"))
        );
        when(adminBankService.getIntegrationHealth()).thenReturn(health);

        mockMvc.perform(get("/api/v1/admin/integrations/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus").value("UP"))
                .andExpect(jsonPath("$.components[0].name").value("Bank A"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/transfers/uncertain-queue returns uncertain queue")
    void getUncertainQueue() throws Exception {
        when(adminTransferService.getUncertainQueue(any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/admin/transfers/uncertain-queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/admin/transfers/{id}/reconcile triggers reconciliation")
    void triggerReconciliation() throws Exception {
        ReconcileResultResponse res = new ReconcileResultResponse(
                10L, "TX-REF-1", TransactionStatus.REQUIRES_RECONCILIATION, TransactionStatus.COMPLETED, true, "OK"
        );
        when(adminTransferService.triggerReconciliation(eq(10L), eq(ADMIN_ID), any()))
                .thenReturn(res);

        mockMvc.perform(post("/api/v1/admin/transfers/10/reconcile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(true))
                .andExpect(jsonPath("$.resolvedStatus").value("COMPLETED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/transfers/{id} rejects editing bank-confirmed transaction with 400")
    void cannotEditConfirmedTransaction() throws Exception {
        doThrow(new ValidationException("CANNOT_EDIT_CONFIRMED_TRANSACTION",
                "Bank-confirmed transactions cannot be modified by admins"))
                .when(adminTransferService).validateNotBankConfirmed(10L);

        mockMvc.perform(patch("/api/v1/admin/transfers/10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_EDIT_CONFIRMED_TRANSACTION"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/risk/rules returns configurable risk rules")
    void getRiskRules() throws Exception {
        RiskRulesResponse rules = new RiskRulesResponse(
                1L, new BigDecimal("50000.00"), new BigDecimal("100000.00"), 5, 24,
                new BigDecimal("10000.00"), new BigDecimal("25000.00"), 60, true, Instant.now(), null
        );
        when(riskManagementService.getRiskRules()).thenReturn(rules);

        mockMvc.perform(get("/api/v1/admin/risk/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxTransactionAmount").value(50000.00))
                .andExpect(jsonPath("$.maxVelocityCountPerHour").value(5));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/risk/rules updates configurable risk rules")
    void updateRiskRules() throws Exception {
        RiskRulesResponse rules = new RiskRulesResponse(
                1L, new BigDecimal("60000.00"), new BigDecimal("100000.00"), 10, 24,
                new BigDecimal("10000.00"), new BigDecimal("25000.00"), 60, true, Instant.now(), ADMIN_ID
        );
        when(riskManagementService.updateRiskRules(any(), eq(ADMIN_ID), any())).thenReturn(rules);

        UpdateRiskRulesRequest req = new UpdateRiskRulesRequest(
                new BigDecimal("60000.00"), null, 10, null, null, null, null, null
        );

        mockMvc.perform(put("/api/v1/admin/risk/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxTransactionAmount").value(60000.00))
                .andExpect(jsonPath("$.maxVelocityCountPerHour").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/admin/fraud-alerts returns fraud alerts list")
    void getFraudAlerts() throws Exception {
        FraudAlertResponse alert = new FraudAlertResponse(
                1L, 10L, "+9477***1111", 50L, "TX-REF-50", FraudAlertType.VELOCITY_EXCEEDED,
                FraudAlertSeverity.HIGH, FraudAlertStatus.OPEN, "Velocity breached", null,
                Instant.now(), null, null, null
        );
        when(riskManagementService.getFraudAlerts(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(alert), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/admin/fraud-alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].alertType").value("VELOCITY_EXCEEDED"))
                .andExpect(jsonPath("$.content[0].severity").value("HIGH"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/risk/fraud-alerts/{id}/status updates alert status")
    void updateFraudAlertStatus() throws Exception {
        FraudAlertResponse alert = new FraudAlertResponse(
                1L, 10L, "+9477***1111", 50L, "TX-REF-50", FraudAlertType.VELOCITY_EXCEEDED,
                FraudAlertSeverity.HIGH, FraudAlertStatus.RESOLVED, "Velocity breached", null,
                Instant.now(), Instant.now(), ADMIN_ID, "Reviewed by compliance"
        );
        when(riskManagementService.updateFraudAlertStatus(eq(1L), any(), eq(ADMIN_ID), any()))
                .thenReturn(alert);

        UpdateFraudAlertStatusRequest req = new UpdateFraudAlertStatusRequest(
                FraudAlertStatus.RESOLVED, "Reviewed by compliance"
        );

        mockMvc.perform(patch("/api/v1/admin/risk/fraud-alerts/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionNotes").value("Reviewed by compliance"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/audit-log returns paginated audit log entries")
    void searchAuditLogs() throws Exception {
        AdminAuditLogResponse entry = new AdminAuditLogResponse(
                1L, 999L, "ADMIN_USER_STATUS_CHANGE", "USER", 1L, "{}", "127.0.0.1", Instant.now()
        );
        when(adminAuditService.searchAuditLogs(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 50), 1));

        mockMvc.perform(get("/api/v1/admin/audit-log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("ADMIN_USER_STATUS_CHANGE"))
                .andExpect(jsonPath("$.content[0].actorId").value(999));
    }
}
