package com.lankapay.support.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.common.web.GlobalExceptionHandler;
import com.lankapay.support.domain.ConversationStatus;
import com.lankapay.support.domain.MessageSender;
import com.lankapay.support.domain.SupportCategory;
import com.lankapay.support.service.SupportService;
import com.lankapay.support.web.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupportController and AdminSupportController standalone WebMvc Tests")
class SupportControllerTest {

    @Mock
    private SupportService supportService;

    private MockMvc publicMockMvc;
    private MockMvc adminMockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        SupportController supportController = new SupportController(supportService, null);
        AdminSupportController adminSupportController = new AdminSupportController(supportService);

        publicMockMvc = MockMvcBuilders.standaloneSetup(supportController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        adminMockMvc = MockMvcBuilders.standaloneSetup(adminSupportController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/support/conversations creates conversation")
    void testCreateConversation() throws Exception {
        CreateConversationRequest req = new CreateConversationRequest(
                SupportCategory.PAYMENTS,
                "Payment inquiry",
                "How does recipient lookup work?",
                null
        );

        SupportConversationDto dto = new SupportConversationDto(
                "conv_101",
                1L,
                ConversationStatus.OPEN,
                SupportCategory.PAYMENTS,
                "Payment inquiry",
                null,
                Instant.now(),
                Instant.now(),
                0,
                null
        );

        when(supportService.createConversation(anyLong(), any(CreateConversationRequest.class)))
                .thenReturn(dto);

        publicMockMvc.perform(post("/api/v1/support/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("conv_101"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("GET /api/v1/support/conversations/{id}/messages supports polling via ?after=")
    void testGetMessagesPolling() throws Exception {
        SupportMessageDto msg = new SupportMessageDto(
                "msg_1",
                "conv_101",
                MessageSender.BOT,
                "LankaPay ID uses a central proxy registry.",
                Instant.now(),
                null,
                null
        );

        when(supportService.getMessages(anyLong(), eq("conv_101"), any()))
                .thenReturn(List.of(msg));

        publicMockMvc.perform(get("/api/v1/support/conversations/conv_101/messages")
                        .param("after", "2026-10-13T10:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("msg_1"))
                .andExpect(jsonPath("$[0].sender").value("BOT"));
    }

    @Test
    @DisplayName("GET /api/v1/support/faq returns curated FAQs")
    void testGetFaqs() throws Exception {
        FaqItemDto faq = new FaqItemDto(
                "faq_lookup",
                "How lookup works",
                "How does recipient lookup work?",
                "Central proxy registry answer"
        );

        when(supportService.getFaqs()).thenReturn(List.of(faq));

        publicMockMvc.perform(get("/api/v1/support/faq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("faq_lookup"))
                .andExpect(jsonPath("$[0].title").value("How lookup works"));
    }

    @Test
    @DisplayName("Admin inbox endpoints: GET conversations, detail, reply, resolve")
    void testAdminEndpoints() throws Exception {
        SupportConversationDto convDto = new SupportConversationDto(
                "conv_101",
                1L,
                ConversationStatus.WAITING,
                SupportCategory.PAYMENTS,
                "Pending payment help",
                "TX-9900",
                Instant.now(),
                Instant.now(),
                1,
                null
        );

        when(supportService.adminListConversations(any(), any()))
                .thenReturn(List.of(convDto));

        adminMockMvc.perform(get("/api/v1/admin/support/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("conv_101"))
                .andExpect(jsonPath("$[0].status").value("WAITING"));

        AdminSupportConversationDetailDto detail = new AdminSupportConversationDetailDto(
                convDto,
                List.of(),
                new MaskedTransactionSummaryDto("TX-9900", new java.math.BigDecimal("1500.00"), "LKR", "PENDING", "+94 77 •••• 123", "+94 71 •••• 456", Instant.now(), true),
                "Security Warning"
        );

        when(supportService.adminGetConversationDetail(anyLong(), eq("conv_101"), anyString()))
                .thenReturn(detail);

        adminMockMvc.perform(get("/api/v1/admin/support/conversations/conv_101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.id").value("conv_101"))
                .andExpect(jsonPath("$.attachedTransaction.reference").value("TX-9900"));

        SupportMessageDto agentReply = new SupportMessageDto(
                "msg_reply_1",
                "conv_101",
                MessageSender.AGENT,
                "We are checking the clearing network.",
                Instant.now(),
                null,
                null
        );

        when(supportService.adminReply(anyLong(), eq("conv_101"), any(), anyString()))
                .thenReturn(agentReply);

        AdminSupportReplyRequest replyReq = new AdminSupportReplyRequest("We are checking the clearing network.");

        adminMockMvc.perform(post("/api/v1/admin/support/conversations/conv_101/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sender").value("AGENT"))
                .andExpect(jsonPath("$.text").value("We are checking the clearing network."));
    }
}
