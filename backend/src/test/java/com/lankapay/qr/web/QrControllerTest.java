package com.lankapay.qr.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.common.web.GlobalExceptionHandler;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.repository.UserRepository;
import com.lankapay.qr.domain.QrStatus;
import com.lankapay.qr.domain.QrType;
import com.lankapay.qr.service.QrService;
import com.lankapay.qr.web.dto.CreateQrReferenceRequest;
import com.lankapay.qr.web.dto.QrReferenceResponse;
import com.lankapay.qr.web.dto.ResolvedQrRecipientResponse;
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
class QrControllerTest {

    @Mock QrService qrService;
    @Mock UserRepository userRepository;

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    User merchant;

    static final Long USER_ID = 42L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        merchant = new User("+94771234567", "Kamal Perera", "199012345678", "$2a$12$hash");
        ReflectionTestUtils.setField(merchant, "id", USER_ID);

        QrController controller = new QrController(qrService, userRepository);

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
    @DisplayName("POST /api/v1/qr/references creates opaque QR reference")
    void testCreateQrReference() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(merchant));

        QrReferenceResponse response = new QrReferenceResponse(
                "QREF-1234-abcd", QrType.DYNAMIC, "LANKAPAY:QR:QREF-1234-abcd",
                USER_ID, "Kamal Perera", "+9477***4567",
                new BigDecimal("1500.00"), "LKR", "Table 5 bill",
                Instant.now().plusSeconds(900), true, QrStatus.ACTIVE, Instant.now());

        when(qrService.createQrReference(any(CreateQrReferenceRequest.class), eq(merchant)))
                .thenReturn(response);

        CreateQrReferenceRequest request = new CreateQrReferenceRequest(
                QrType.DYNAMIC, new BigDecimal("1500.00"), "LKR", "Table 5 bill", 15);

        mockMvc.perform(post("/api/v1/qr/references")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("QREF-1234-abcd"))
                .andExpect(jsonPath("$.qrPayload").value("LANKAPAY:QR:QREF-1234-abcd"))
                .andExpect(jsonPath("$.amount").value(1500.00))
                .andExpect(jsonPath("$.oneTimeUse").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/qr/references/{reference} resolves recipient without executing transfer")
    void testResolveQrReference() throws Exception {
        ResolvedQrRecipientResponse response = new ResolvedQrRecipientResponse(
                "QREF-1234-abcd", QrType.DYNAMIC, USER_ID, "Kamal Perera", "+9477***4567",
                new BigDecimal("1500.00"), "LKR", "Table 5 bill",
                Instant.now().plusSeconds(900), QrStatus.ACTIVE, true,
                "Recipient resolved successfully. Proceed with transfer confirmation and authorization.");

        when(qrService.resolveQrReference("QREF-1234-abcd")).thenReturn(response);

        mockMvc.perform(get("/api/v1/qr/references/QREF-1234-abcd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("QREF-1234-abcd"))
                .andExpect(jsonPath("$.recipientId").value(42))
                .andExpect(jsonPath("$.recipientDisplayName").value("Kamal Perera"))
                .andExpect(jsonPath("$.maskedPhone").value("+9477***4567"))
                .andExpect(jsonPath("$.amount").value(1500.00))
                .andExpect(jsonPath("$.message").value("Recipient resolved successfully. Proceed with transfer confirmation and authorization."));
    }

    @Test
    @DisplayName("GET /api/v1/qr/references/{reference} with tampered reference returns 404")
    void testTamperedReferenceReturns404() throws Exception {
        when(qrService.resolveQrReference("QREF-TAMPERED"))
                .thenThrow(new ResourceNotFoundException("QR_NOT_FOUND", "QR reference is invalid or unknown"));

        mockMvc.perform(get("/api/v1/qr/references/QREF-TAMPERED"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QR_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/qr/references/{reference} with used reference returns 409 Conflict")
    void testUsedReferenceReturns409() throws Exception {
        when(qrService.resolveQrReference("QREF-USED"))
                .thenThrow(new ConflictException("QR_ALREADY_USED", "This one-time QR code has already been used"));

        mockMvc.perform(get("/api/v1/qr/references/QREF-USED"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QR_ALREADY_USED"));
    }

    @Test
    @DisplayName("GET /api/v1/qr/references/{reference} with expired reference returns 400 Bad Request")
    void testExpiredReferenceReturns400() throws Exception {
        when(qrService.resolveQrReference("QREF-EXPIRED"))
                .thenThrow(new ValidationException("QR_EXPIRED", "This QR code has expired"));

        mockMvc.perform(get("/api/v1/qr/references/QREF-EXPIRED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QR_EXPIRED"));
    }
}
