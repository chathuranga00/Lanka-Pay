package com.lankapay.qr.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.web.GlobalExceptionHandler;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.repository.UserRepository;
import com.lankapay.qr.domain.PaymentRequestStatus;
import com.lankapay.qr.service.PaymentRequestService;
import com.lankapay.qr.web.dto.CreatePaymentRequestDto;
import com.lankapay.qr.web.dto.PaymentRequestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentRequestControllerTest {

    @Mock PaymentRequestService paymentRequestService;
    @Mock UserRepository userRepository;

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    User requester;

    static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        requester = new User("+94771111111", "Requester User", "199011111111", "$2a$12$hash");
        ReflectionTestUtils.setField(requester, "id", USER_ID);

        PaymentRequestController controller = new PaymentRequestController(paymentRequestService, userRepository);

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
                .setCustomArgumentResolvers(authPrincipalResolver)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/payment-requests creates payment request with opaque reference")
    void testCreatePaymentRequest() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(requester));

        PaymentRequestResponse response = new PaymentRequestResponse(
                501L, "PREQ-1234-abcd", USER_ID, "Requester User",
                2L, "Nimal Silva", new BigDecimal("2500.00"), "LKR",
                "Invoice #1042", PaymentRequestStatus.PENDING, "QREF-1234-abcd",
                "LANKAPAY:QR:QREF-1234-abcd", null,
                Instant.now().plusSeconds(900), Instant.now());

        when(paymentRequestService.createPaymentRequest(any(CreatePaymentRequestDto.class), eq(requester)))
                .thenReturn(response);

        CreatePaymentRequestDto request = new CreatePaymentRequestDto(
                new BigDecimal("2500.00"), "LKR", 2L, null, "Invoice #1042", 15);

        mockMvc.perform(post("/api/v1/payment-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.reference").value("PREQ-1234-abcd"))
                .andExpect(jsonPath("$.amount").value(2500.00))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.qrReference").value("QREF-1234-abcd"));
    }

    @Test
    @DisplayName("GET /api/v1/payment-requests/{id} returns payment request details for authorized user")
    void testGetPaymentRequestAuthorized() throws Exception {
        PaymentRequestResponse response = new PaymentRequestResponse(
                501L, "PREQ-1234-abcd", USER_ID, "Requester User",
                2L, "Nimal Silva", new BigDecimal("2500.00"), "LKR",
                "Invoice #1042", PaymentRequestStatus.PENDING, "QREF-1234-abcd",
                "LANKAPAY:QR:QREF-1234-abcd", null,
                Instant.now().plusSeconds(900), Instant.now());

        when(paymentRequestService.getPaymentRequest(501L, USER_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/payment-requests/501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.reference").value("PREQ-1234-abcd"))
                .andExpect(jsonPath("$.amount").value(2500.00));
    }

    @Test
    @DisplayName("GET /api/v1/payment-requests/{id} returns 403 Forbidden for unauthorized user")
    void testGetPaymentRequestUnauthorized() throws Exception {
        when(paymentRequestService.getPaymentRequest(501L, USER_ID))
                .thenThrow(new AuthorizationException("FORBIDDEN", "You do not have authorization to view this payment request"));

        mockMvc.perform(get("/api/v1/payment-requests/501"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/v1/payment-requests/{id} returns 404 Not Found if request does not exist")
    void testGetPaymentRequestNotFound() throws Exception {
        when(paymentRequestService.getPaymentRequest(999L, USER_ID))
                .thenThrow(new ResourceNotFoundException("PAYMENT_REQUEST_NOT_FOUND", "Payment request not found with id: 999"));

        mockMvc.perform(get("/api/v1/payment-requests/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_REQUEST_NOT_FOUND"));
    }
}
