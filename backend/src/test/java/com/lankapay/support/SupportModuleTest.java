package com.lankapay.support;

import com.lankapay.audit.domain.AuditRecord;
import com.lankapay.audit.domain.AuditStore;
import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.RateLimitException;
import com.lankapay.notification.domain.NotificationStore;
import com.lankapay.support.domain.ConversationStatus;
import com.lankapay.support.domain.MessageSender;
import com.lankapay.support.domain.SupportCategory;
import com.lankapay.support.service.DataMaskingFilter;
import com.lankapay.support.service.FaqBotEngine;
import com.lankapay.support.service.SupportRateLimiter;
import com.lankapay.support.service.SupportService;
import com.lankapay.support.store.InMemorySupportStore;
import com.lankapay.support.store.SupportStore;
import com.lankapay.support.web.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SupportModuleTest {

    private SupportStore supportStore;
    private FaqBotEngine faqBotEngine;
    private SupportRateLimiter rateLimiter;
    private AuditStore auditStore;
    private AuditService auditService;
    private NotificationStore notificationStore;
    private SupportService supportService;

    @BeforeEach
    void setUp() {
        supportStore = new InMemorySupportStore();
        faqBotEngine = new FaqBotEngine();
        rateLimiter = new SupportRateLimiter(5); // capacity of 5 per minute for tests
        auditStore = mock(AuditStore.class);
        auditService = new AuditService(auditStore);
        notificationStore = mock(NotificationStore.class);

        supportService = new SupportService(
                supportStore,
                faqBotEngine,
                rateLimiter,
                auditService,
                notificationStore,
                null
        );
    }

    @Test
    @DisplayName("1. Sensitive Data Masking: 13-19 digit card numbers and 6-digit OTP codes are masked before storing")
    void testDataMaskingFilter() {
        String input = "My card is 4111222233334444 and CVV is 123, OTP received was 654321. Also tested 4111-2222-3333-4444.";
        String masked = DataMaskingFilter.mask(input);

        assertThat(masked).doesNotContain("4111222233334444");
        assertThat(masked).doesNotContain("4111-2222-3333-4444");
        assertThat(masked).doesNotContain("654321");
        assertThat(masked).contains("[CARD_NUMBER_MASKED]");
        assertThat(masked).contains("[OTP_MASKED]");

        // Persist via SupportService
        CreateConversationRequest req = new CreateConversationRequest(
                SupportCategory.CARDS,
                "Card issue",
                "Please help with card 5241 1234 5678 9012 and security code 123456",
                null
        );
        SupportConversationDto conv = supportService.createConversation(100L, req);

        List<SupportMessageDto> messages = supportService.getMessages(100L, conv.id(), null);
        assertThat(messages).isNotEmpty();
        SupportMessageDto userMsg = messages.stream().filter(m -> m.sender() == MessageSender.USER).findFirst().orElseThrow();

        assertThat(userMsg.text()).doesNotContain("5241 1234 5678 9012");
        assertThat(userMsg.text()).doesNotContain("123456");
        assertThat(userMsg.text()).contains("[CARD_NUMBER_MASKED]");
        assertThat(userMsg.text()).contains("[OTP_MASKED]");
    }

    @Test
    @DisplayName("2. Access Control: Users can only view and post to their own conversations")
    void testAccessControl() {
        CreateConversationRequest req = new CreateConversationRequest(
                SupportCategory.GENERAL,
                "User 100 private inquiry",
                "Hello, I need help with my account",
                null
        );
        SupportConversationDto conv = supportService.createConversation(100L, req);

        // User 200 tries to view conversation -> AccessDeniedException
        assertThatThrownBy(() -> supportService.getUserConversation(200L, conv.id()))
                .isInstanceOf(AccessDeniedException.class);

        // User 200 tries to read messages -> AccessDeniedException
        assertThatThrownBy(() -> supportService.getMessages(200L, conv.id(), null))
                .isInstanceOf(AccessDeniedException.class);

        // User 200 tries to send message -> AccessDeniedException
        SendMessageRequest sendReq = new SendMessageRequest("Attempting unauthorized reply", null);
        assertThatThrownBy(() -> supportService.sendMessage(200L, conv.id(), sendReq))
                .isInstanceOf(AccessDeniedException.class);

        // User 200 tries to resolve -> AccessDeniedException
        assertThatThrownBy(() -> supportService.resolveConversation(200L, conv.id()))
                .isInstanceOf(AccessDeniedException.class);

        // User 100 succeeds
        SupportConversationDto retrieved = supportService.getUserConversation(100L, conv.id());
        assertThat(retrieved.id()).isEqualTo(conv.id());
    }

    @Test
    @DisplayName("3. Rate Limiting: Bursting messages triggers RateLimitException")
    void testRateLimiting() {
        CreateConversationRequest req = new CreateConversationRequest(
                SupportCategory.GENERAL,
                "Rate limit test",
                "Initial message 1",
                null
        );
        SupportConversationDto conv = supportService.createConversation(300L, req);

        // Capacity is 5: 1 consumed on create, consume remaining 4
        for (int i = 0; i < 4; i++) {
            supportService.sendMessage(300L, conv.id(), new SendMessageRequest("Message " + i, null));
        }

        // 6th message exceeds limit
        assertThatThrownBy(() ->
                supportService.sendMessage(300L, conv.id(), new SendMessageRequest("Burst message", null))
        ).isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("4. Status Flow: OPEN -> FAQ answers -> WAITING on agent request -> RESOLVED -> Reopen on new message")
    void testStatusFlow() {
        // Step 1: User asks FAQ question
        CreateConversationRequest req = new CreateConversationRequest(
                SupportCategory.PAYMENTS,
                "Lookup Question",
                "How does recipient lookup work?",
                null
        );
        SupportConversationDto conv = supportService.createConversation(400L, req);
        assertThat(conv.status()).isEqualTo(ConversationStatus.OPEN);

        List<SupportMessageDto> msgs = supportService.getMessages(400L, conv.id(), null);
        SupportMessageDto botReply = msgs.stream().filter(m -> m.sender() == MessageSender.BOT).findFirst().orElseThrow();
        assertThat(botReply.text()).contains("central proxy registry");

        // Step 2: User asks for human agent -> WAITING
        supportService.sendMessage(400L, conv.id(), new SendMessageRequest("I want to talk to an agent please", null));
        SupportConversationDto waitingConv = supportService.getUserConversation(400L, conv.id());
        assertThat(waitingConv.status()).isEqualTo(ConversationStatus.WAITING);

        // Step 3: Admin replies -> OPEN
        supportService.adminReply(9999L, conv.id(), new AdminSupportReplyRequest("Hello, I am support agent Sarah. How can I help?"), "127.0.0.1");
        SupportConversationDto activeConv = supportService.getUserConversation(400L, conv.id());
        assertThat(activeConv.status()).isEqualTo(ConversationStatus.OPEN);

        // Step 4: Customer marks resolved -> RESOLVED
        SupportConversationDto resolvedConv = supportService.resolveConversation(400L, conv.id());
        assertThat(resolvedConv.status()).isEqualTo(ConversationStatus.RESOLVED);

        // Step 5: Customer sends follow up -> Re-opens to OPEN
        supportService.sendMessage(400L, conv.id(), new SendMessageRequest("Actually one more quick question", null));
        SupportConversationDto reopenedConv = supportService.getUserConversation(400L, conv.id());
        assertThat(reopenedConv.status()).isEqualTo(ConversationStatus.OPEN);
    }

    @Test
    @DisplayName("5. Audit Logging: Every agent view and reply is audited with AuditService")
    void testAuditLogging() {
        CreateConversationRequest req = new CreateConversationRequest(
                SupportCategory.PAYMENTS,
                "Transfer query",
                "Transaction issue",
                "TX-20261013-9090"
        );
        SupportConversationDto conv = supportService.createConversation(500L, req);

        // Agent Views Conversation
        AdminSupportConversationDetailDto detail = supportService.adminGetConversationDetail(8888L, conv.id(), "192.168.1.50");
        assertThat(detail.attachedTransaction()).isNotNull();
        assertThat(detail.attachedTransaction().reference()).isEqualTo("TX-20261013-9090");

        // Verify Audit Log on View
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditStore, atLeastOnce()).save(captor.capture());

        boolean viewAudited = captor.getAllValues().stream()
                .anyMatch(r -> "SUPPORT_VIEW_CONVERSATION".equals(r.eventType()) && Long.valueOf(8888L).equals(r.actorId()));
        assertThat(viewAudited).isTrue();

        // Agent Replies
        supportService.adminReply(8888L, conv.id(), new AdminSupportReplyRequest("We are investigating TX-20261013-9090."), "192.168.1.50");

        verify(auditStore, atLeast(2)).save(captor.capture());
        boolean replyAudited = captor.getAllValues().stream()
                .anyMatch(r -> "SUPPORT_REPLY".equals(r.eventType()) && Long.valueOf(8888L).equals(r.actorId()));
        assertThat(replyAudited).isTrue();

        // Verify Privacy-Safe Notification Sent to User
        verify(notificationStore).saveNotification(argThat(n ->
                n.userId().equals(500L) &&
                        "You have a new reply".equals(n.message()) &&
                        "Support Update".equals(n.title())
        ));
    }
}
