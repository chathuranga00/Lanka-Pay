package com.lankapay.transfer.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.web.GlobalExceptionHandler;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.repository.UserRepository;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.Transfer;
import com.lankapay.transfer.service.ReconciliationService;
import com.lankapay.transfer.service.ReversalService;
import com.lankapay.transfer.service.TransferService;
import com.lankapay.transfer.web.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TransferControllerTest {

    @Mock TransferService transferService;
    @Mock ReconciliationService reconciliationService;
    @Mock ReversalService reversalService;
    @Mock UserRepository userRepository;

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    User sender;

    static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        sender = new User("+94771234567", "Sender User", "199012345678", "$2a$12$hash");
        ReflectionTestUtils.setField(sender, "id", USER_ID);
        ReflectionTestUtils.setField(sender, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(sender, "role", UserRole.CUSTOMER);

        TransferController controller = new TransferController(
                transferService, reconciliationService, reversalService, userRepository);

        HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return USER_ID;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authPrincipalResolver, new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("POST /transfers/quote returns quote with fee and total")
    void testGetQuote() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(sender));

        QuoteResponse quoteResponse = new QuoteResponse(
                "QUO-1234", 2L, "Kamal P.", "+9477***4567",
                new BigDecimal("1500.00"), BigDecimal.ZERO, new BigDecimal("1500.00"),
                "LKR", Instant.now().plusSeconds(900), true);

        when(transferService.getQuote(any(QuoteRequest.class), eq(sender)))
                .thenReturn(quoteResponse);

        QuoteRequest request = new QuoteRequest(2L, null, new BigDecimal("1500.00"), "LKR");

        mockMvc.perform(post("/api/v1/transfers/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteReference").value("QUO-1234"))
                .andExpect(jsonPath("$.amount").value(1500.00))
                .andExpect(jsonPath("$.simulated").value(true));
    }

    @Test
    @DisplayName("POST /transfers with Idempotency-Key and step-up auth succeeds")
    void testInitiateTransferSuccess() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(sender));

        TransferResponse response = new TransferResponse(
                100L, "uuid-ref-1", TransactionStatus.COMPLETED,
                new BigDecimal("1500.00"), "LKR", 1L, "+9477***1234",
                2L, "Kamal P.", "SIM-TX-1", "SIM-PROV-1",
                "Lunch split", null, true, Instant.now(), Instant.now());

        when(transferService.initiateTransfer(eq("IDEM-123"), eq("123456"), any(InitiateTransferRequest.class), eq(sender), anyString()))
                .thenReturn(response);

        InitiateTransferRequest request = new InitiateTransferRequest(
                2L, null, new BigDecimal("1500.00"), "LKR", null, "Lunch split", "123456");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "IDEM-123")
                        .header("X-Step-Up-Pin", "123456")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(100))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.bankTxRef").value("SIM-TX-1"));
    }

    @Test
    @DisplayName("POST /transfers missing Idempotency-Key returns 400 Bad Request")
    void testMissingIdempotencyKey() throws Exception {
        InitiateTransferRequest request = new InitiateTransferRequest(
                2L, null, new BigDecimal("1500.00"), "LKR", null, "Lunch split", "123456");

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /transfers with duplicate idempotency key and conflicting payload returns 409 Conflict")
    void testIdempotencyConflictReturns409() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(sender));

        when(transferService.initiateTransfer(anyString(), any(), any(InitiateTransferRequest.class), any(User.class), anyString()))
                .thenThrow(new ConflictException("IDEMPOTENCY_CONFLICT", "Key reused with different payload"));

        InitiateTransferRequest request = new InitiateTransferRequest(
                2L, null, new BigDecimal("2000.00"), "LKR", null, "Dinner", "123456");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", "IDEM-REUSED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    @DisplayName("GET /transfers/{id} returns transfer details")
    void testGetTransferById() throws Exception {
        TransferResponse response = new TransferResponse(
                100L, "uuid-ref-1", TransactionStatus.COMPLETED,
                new BigDecimal("1500.00"), "LKR", 1L, "+9477***1234",
                2L, "Kamal P.", "SIM-TX-1", "SIM-PROV-1",
                "Lunch split", null, true, Instant.now(), Instant.now());

        when(transferService.getTransferById(100L, USER_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/transfers/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(100))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /transfers with pagination returns transfer list")
    void testListTransfers() throws Exception {
        TransferResponse response = new TransferResponse(
                100L, "uuid-ref-1", TransactionStatus.COMPLETED,
                new BigDecimal("1500.00"), "LKR", 1L, "+9477***1234",
                2L, "Kamal P.", "SIM-TX-1", "SIM-PROV-1",
                "Lunch split", null, true, Instant.now(), Instant.now());

        when(transferService.getTransfers(eq(USER_ID), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/transfers?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transferId").value(100))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("POST /transfers/{id}/reconcile triggers reconciliation")
    void testReconcileTransfer() throws Exception {
        ReconcileResultResponse result = new ReconcileResultResponse(
                100L, "uuid-ref-1", TransactionStatus.REQUIRES_RECONCILIATION,
                TransactionStatus.COMPLETED, true, "Reconciled successfully to COMPLETED");

        when(reconciliationService.reconcileTransfer(100L)).thenReturn(result);

        mockMvc.perform(post("/api/v1/transfers/100/reconcile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(true))
                .andExpect(jsonPath("$.resolvedStatus").value("COMPLETED"));
    }

    @Test
    @DisplayName("POST /transfers/{id}/reversal requests reversal")
    void testRequestReversal() throws Exception {
        Transfer transfer = new Transfer(
                "uuid-ref-1", sender, sender, new BigDecimal("1500.00"), "LKR", null);
        ReflectionTestUtils.setField(transfer, "id", 100L);
        ReflectionTestUtils.setField(transfer, "status", TransactionStatus.REVERSAL_PENDING);

        when(reversalService.requestReversal(eq(100L), eq(USER_ID), eq("Disputed transaction"), anyString()))
                .thenReturn(transfer);

        ReversalRequest request = new ReversalRequest("Disputed transaction");

        mockMvc.perform(post("/api/v1/transfers/100/reversal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVERSAL_PENDING"));
    }
}
