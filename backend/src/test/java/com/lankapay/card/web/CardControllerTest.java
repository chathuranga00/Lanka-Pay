package com.lankapay.card.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.card.domain.Card;
import com.lankapay.card.domain.CardKind;
import com.lankapay.card.domain.CardStatus;
import com.lankapay.card.service.CardService;
import com.lankapay.card.web.dto.*;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardController Web MVC tests")
class CardControllerTest {

    @Mock CardService cardService;

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    static final Long USER_ID = 1L;
    static final Long CARD_ID = 101L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        CardController controller = new CardController(cardService);

        HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && parameter.getParameterType().equals(Long.class);
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
                .setCustomArgumentResolvers(principalResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    Card sampleCard(Long id, CardKind kind, CardStatus status) {
        return new Card(
                id,
                USER_ID,
                500L,
                "token-abc",
                "0010",
                12,
                2028,
                "TEST CARD",
                "My Card",
                kind,
                status,
                true,
                Card.DEFAULT_PER_PAYMENT_LIMIT_MINOR,
                Card.DEFAULT_DAILY_LIMIT_MINOR,
                Instant.now(),
                Instant.now(),
                status == CardStatus.ACTIVE ? Instant.now() : null,
                null,
                1L,
                "chall-1",
                true
        );
    }

    @Test
    @DisplayName("POST /api/v1/cards – link card returns 201 Created with masked data")
    void linkCardReturns201() throws Exception {
        Card card = sampleCard(CARD_ID, CardKind.LINKED, CardStatus.PENDING_VERIFICATION);
        when(cardService.linkCard(eq(USER_ID), any(), any(), any())).thenReturn(card);

        CreateCardRequest request = new CreateCardRequest("token-abc", "My Card", 500L, true, "123456");

        mockMvc.perform(post("/api/v1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(CARD_ID))
                .andExpect(jsonPath("$.last4").value("0010"))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.cardToken").doesNotExist()) // Masked view must NOT have cardToken
                .andExpect(jsonPath("$.simulated").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/cards/{id}/verify – verify OTP returns 200 OK")
    void verifyCardReturns200() throws Exception {
        Card card = sampleCard(CARD_ID, CardKind.LINKED, CardStatus.ACTIVE);
        when(cardService.verifyCard(eq(CARD_ID), eq(USER_ID), eq("123456"), any())).thenReturn(card);

        VerifyCardRequest request = new VerifyCardRequest("123456");

        mockMvc.perform(post("/api/v1/cards/{id}/verify", CARD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CARD_ID))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/v1/cards – list cards returns 200 OK")
    void listCardsReturns200() throws Exception {
        Card card = sampleCard(CARD_ID, CardKind.LINKED, CardStatus.ACTIVE);
        when(cardService.listCards(USER_ID)).thenReturn(List.of(card));

        mockMvc.perform(get("/api/v1/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(CARD_ID))
                .andExpect(jsonPath("$[0].last4").value("0010"));
    }

    @Test
    @DisplayName("GET /api/v1/cards/{id} – returns 200 OK when found")
    void getCardReturns200() throws Exception {
        Card card = sampleCard(CARD_ID, CardKind.LINKED, CardStatus.ACTIVE);
        when(cardService.getCard(CARD_ID, USER_ID)).thenReturn(card);

        mockMvc.perform(get("/api/v1/cards/{id}", CARD_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CARD_ID))
                .andExpect(jsonPath("$.last4").value("0010"));
    }

    @Test
    @DisplayName("GET /api/v1/cards/{id} – returns 404 when card not found or not owned")
    void getCardNotFoundReturns404() throws Exception {
        when(cardService.getCard(CARD_ID, USER_ID))
                .thenThrow(new ResourceNotFoundException("CARD_NOT_FOUND", "Card not found"));

        mockMvc.perform(get("/api/v1/cards/{id}", CARD_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /api/v1/cards/{id} – update card returns 200 OK")
    void patchCardReturns200() throws Exception {
        Card card = sampleCard(CARD_ID, CardKind.LINKED, CardStatus.ACTIVE);
        when(cardService.patchCard(eq(CARD_ID), eq(USER_ID), any(), any(), any())).thenReturn(card);

        PatchCardRequest request = new PatchCardRequest("Updated Label", 4_000_000L, 15_000_000L, true, null);

        mockMvc.perform(patch("/api/v1/cards/{id}", CARD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CARD_ID));
    }

    @Test
    @DisplayName("POST /api/v1/cards/{id}/freeze – freeze card returns 200 OK")
    void freezeCardReturns200() throws Exception {
        Card card = sampleCard(CARD_ID, CardKind.LINKED, CardStatus.FROZEN);
        when(cardService.freezeCard(eq(CARD_ID), eq(USER_ID), any())).thenReturn(card);

        mockMvc.perform(post("/api/v1/cards/{id}/freeze", CARD_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
    }

    @Test
    @DisplayName("POST /api/v1/cards/{id}/unfreeze – unfreeze card returns 200 OK")
    void unfreezeCardReturns200() throws Exception {
        Card card = sampleCard(CARD_ID, CardKind.LINKED, CardStatus.ACTIVE);
        when(cardService.unfreezeCard(eq(CARD_ID), eq(USER_ID), any(), any(), any())).thenReturn(card);

        mockMvc.perform(post("/api/v1/cards/{id}/unfreeze", CARD_ID)
                        .header("X-Step-Up-Pin", "123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/v1/cards/{id}/reveal – returns details with Cache-Control: no-store")
    void revealCardReturnsNoStoreHeader() throws Exception {
        CardRevealResponse revealResponse = new CardRevealResponse(
                CARD_ID, "0010", "9900000000000010", "123", "12", "29", "TEST CARD", 30, true);
        when(cardService.revealCard(eq(CARD_ID), eq(USER_ID), any(), any(), any())).thenReturn(revealResponse);

        mockMvc.perform(post("/api/v1/cards/{id}/reveal", CARD_ID)
                        .header("X-Step-Up-Pin", "123456"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.pan").value("9900000000000010"))
                .andExpect(jsonPath("$.cvv").value("123"))
                .andExpect(jsonPath("$.expiresInSeconds").value(30));
    }

    @Test
    @DisplayName("POST /api/v1/cards/virtual – issue virtual card returns 201 Created")
    void issueVirtualCardReturns201() throws Exception {
        Card card = sampleCard(CARD_ID, CardKind.VIRTUAL, CardStatus.ACTIVE);
        when(cardService.issueVirtualCard(eq(USER_ID), any(), any(), any())).thenReturn(card);

        VirtualCardIssueRequest request = new VirtualCardIssueRequest(500L, "Online Card", "123456");

        mockMvc.perform(post("/api/v1/cards/virtual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(CARD_ID))
                .andExpect(jsonPath("$.cardKind").value("VIRTUAL"));
    }

    @Test
    @DisplayName("DELETE /api/v1/cards/{id} – remove card returns 204 No Content")
    void removeCardReturns204() throws Exception {
        doNothing().when(cardService).removeCard(eq(CARD_ID), eq(USER_ID), any());

        mockMvc.perform(delete("/api/v1/cards/{id}", CARD_ID))
                .andExpect(status().isNoContent());
    }
}
