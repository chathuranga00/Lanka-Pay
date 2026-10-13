package com.lankapay.support.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.notification.domain.NotificationChannelType;
import com.lankapay.notification.domain.NotificationRecord;
import com.lankapay.notification.domain.NotificationStore;
import com.lankapay.notification.domain.NotificationType;
import com.lankapay.support.domain.*;
import com.lankapay.support.store.SupportStore;
import com.lankapay.support.web.dto.*;
import com.lankapay.transfer.domain.TransactionRecord;
import com.lankapay.transfer.domain.TransactionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class SupportService {

    private static final Logger log = LoggerFactory.getLogger(SupportService.class);

    private final SupportStore supportStore;
    private final FaqBotEngine faqBotEngine;
    private final SupportRateLimiter rateLimiter;
    private final AuditService auditService;
    private final NotificationStore notificationStore;
    private final TransactionStore transactionStore;

    private final AtomicLong conversationIdSeq = new AtomicLong(1000);
    private final AtomicLong messageIdSeq = new AtomicLong(5000);

    @Autowired
    public SupportService(
            SupportStore supportStore,
            FaqBotEngine faqBotEngine,
            SupportRateLimiter rateLimiter,
            @Autowired(required = false) AuditService auditService,
            @Autowired(required = false) NotificationStore notificationStore,
            @Autowired(required = false) TransactionStore transactionStore
    ) {
        this.supportStore = supportStore;
        this.faqBotEngine = faqBotEngine;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.notificationStore = notificationStore;
        this.transactionStore = transactionStore;
    }

    private Long toEntityId(String strId) {
        if (strId == null) return 0L;
        try {
            return Long.parseLong(strId.replaceAll("\\D+", ""));
        } catch (Exception e) {
            return (long) Math.abs(strId.hashCode());
        }
    }

    // ── User Flows ─────────────────────────────────────────────────────────

    public SupportConversationDto createConversation(Long userId, CreateConversationRequest request) {
        if (userId == null) {
            throw new ValidationException("UNAUTHORIZED", "User must be authenticated");
        }

        rateLimiter.checkLimit(userId);

        String convId = "conv_" + conversationIdSeq.incrementAndGet();
        SupportCategory cat = request.category() != null ? request.category() : SupportCategory.GENERAL;
        String subject = (request.subject() != null && !request.subject().isBlank())
                ? request.subject()
                : "Support Inquiry #" + convId;

        // Mask user message before saving
        String maskedText = DataMaskingFilter.mask(request.initialMessage());

        FaqBotEngine.BotAnswer botAnswer = faqBotEngine.answerUserMessage(request.initialMessage());
        ConversationStatus status = botAnswer.transferToAgent()
                ? ConversationStatus.WAITING
                : ConversationStatus.OPEN;

        Instant now = Instant.now();
        SupportConversation conv = new SupportConversation(
                convId,
                userId,
                status,
                cat,
                subject,
                request.attachedTransactionRef(),
                now,
                now
        );
        supportStore.saveConversation(conv);

        // Save User Message
        String userMsgId = "msg_" + messageIdSeq.incrementAndGet();
        SupportMessage userMsg = new SupportMessage(
                userMsgId,
                convId,
                MessageSender.USER,
                maskedText,
                now,
                null,
                request.attachedTransactionRef()
        );
        supportStore.saveMessage(userMsg);

        // Save Bot Reply
        String botMsgId = "msg_" + messageIdSeq.incrementAndGet();
        SupportMessage botMsg = new SupportMessage(
                botMsgId,
                convId,
                MessageSender.BOT,
                botAnswer.text(),
                now.plusMillis(50),
                null,
                null
        );
        supportStore.saveMessage(botMsg);

        return SupportConversationDto.fromDomain(conv, 1, SupportMessageDto.fromDomain(botMsg));
    }

    public List<SupportConversationDto> listUserConversations(Long userId) {
        if (userId == null) return Collections.emptyList();
        return supportStore.findConversationsByUserId(userId).stream()
                .map(c -> {
                    List<SupportMessage> msgs = supportStore.findMessagesByConversationId(c.id(), null);
                    long unread = msgs.stream()
                            .filter(m -> m.sender() != MessageSender.USER && m.readAt() == null)
                            .count();
                    SupportMessageDto last = msgs.isEmpty() ? null : SupportMessageDto.fromDomain(msgs.get(msgs.size() - 1));
                    return SupportConversationDto.fromDomain(c, unread, last);
                })
                .collect(Collectors.toList());
    }

    public SupportConversationDto getUserConversation(Long userId, String conversationId) {
        SupportConversation conv = supportStore.findConversationById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        if (!Objects.equals(conv.userId(), userId)) {
            throw new AccessDeniedException("You do not have permission to view this conversation");
        }

        supportStore.markMessagesRead(conversationId, MessageSender.USER, Instant.now());

        List<SupportMessage> msgs = supportStore.findMessagesByConversationId(conversationId, null);
        SupportMessageDto last = msgs.isEmpty() ? null : SupportMessageDto.fromDomain(msgs.get(msgs.size() - 1));
        return SupportConversationDto.fromDomain(conv, 0, last);
    }

    public List<SupportMessageDto> getMessages(Long userId, String conversationId, Instant after) {
        SupportConversation conv = supportStore.findConversationById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        if (!Objects.equals(conv.userId(), userId)) {
            throw new AccessDeniedException("You do not have permission to view messages for this conversation");
        }

        supportStore.markMessagesRead(conversationId, MessageSender.USER, Instant.now());

        return supportStore.findMessagesByConversationId(conversationId, after).stream()
                .map(SupportMessageDto::fromDomain)
                .collect(Collectors.toList());
    }

    public SupportMessageDto sendMessage(Long userId, String conversationId, SendMessageRequest request) {
        if (userId == null) {
            throw new ValidationException("UNAUTHORIZED", "User must be authenticated");
        }

        rateLimiter.checkLimit(userId);

        SupportConversation conv = supportStore.findConversationById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        if (!Objects.equals(conv.userId(), userId)) {
            throw new AccessDeniedException("You do not have permission to send messages to this conversation");
        }

        // Mask text before saving
        String maskedText = DataMaskingFilter.mask(request.text());

        // Update attached transaction if provided
        if (request.attachedTransactionRef() != null && !request.attachedTransactionRef().isBlank()) {
            conv = conv.withAttachedTransactionRef(request.attachedTransactionRef());
            supportStore.saveConversation(conv);
        }

        // If conversation was RESOLVED, reopen
        if (conv.status() == ConversationStatus.RESOLVED) {
            conv = conv.withStatus(ConversationStatus.OPEN);
            supportStore.saveConversation(conv);
        }

        Instant now = Instant.now();
        String userMsgId = "msg_" + messageIdSeq.incrementAndGet();
        SupportMessage userMsg = new SupportMessage(
                userMsgId,
                conversationId,
                MessageSender.USER,
                maskedText,
                now,
                null,
                request.attachedTransactionRef()
        );
        supportStore.saveMessage(userMsg);

        // Bot handles message if conversation is in OPEN state or asking for agent
        if (conv.status() == ConversationStatus.OPEN || faqBotEngine.isAgentRequest(request.text())) {
            FaqBotEngine.BotAnswer botAnswer = faqBotEngine.answerUserMessage(request.text());
            if (botAnswer.transferToAgent()) {
                conv = conv.withStatus(ConversationStatus.WAITING);
                supportStore.saveConversation(conv);
            }

            String botMsgId = "msg_" + messageIdSeq.incrementAndGet();
            SupportMessage botMsg = new SupportMessage(
                    botMsgId,
                    conversationId,
                    MessageSender.BOT,
                    botAnswer.text(),
                    now.plusMillis(100),
                    null,
                    null
            );
            supportStore.saveMessage(botMsg);
        }

        return SupportMessageDto.fromDomain(userMsg);
    }

    public SupportConversationDto resolveConversation(Long userId, String conversationId) {
        SupportConversation conv = supportStore.findConversationById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        if (!Objects.equals(conv.userId(), userId)) {
            throw new AccessDeniedException("You do not have permission to resolve this conversation");
        }

        conv = conv.withStatus(ConversationStatus.RESOLVED);
        supportStore.saveConversation(conv);

        // System notice
        String botMsgId = "msg_" + messageIdSeq.incrementAndGet();
        SupportMessage closeMsg = new SupportMessage(
                botMsgId,
                conversationId,
                MessageSender.BOT,
                "This conversation has been marked as resolved by the customer. Reply anytime to reopen it.",
                Instant.now(),
                null,
                null
        );
        supportStore.saveMessage(closeMsg);

        return SupportConversationDto.fromDomain(conv, 0, SupportMessageDto.fromDomain(closeMsg));
    }

    public List<FaqItemDto> getFaqs() {
        return faqBotEngine.getAllFaqs().stream()
                .map(FaqItemDto::fromDomain)
                .collect(Collectors.toList());
    }

    public long getUnreadCountForUser(Long userId) {
        if (userId == null) return 0;
        return supportStore.countUnreadMessagesForUser(userId);
    }

    // ── Admin Flows (Audited) ──────────────────────────────────────────────

    public List<SupportConversationDto> adminListConversations(ConversationStatus status, String search) {
        return supportStore.findAllConversations(status, search).stream()
                .map(c -> {
                    List<SupportMessage> msgs = supportStore.findMessagesByConversationId(c.id(), null);
                    long unread = msgs.stream()
                            .filter(m -> m.sender() == MessageSender.USER && m.readAt() == null)
                            .count();
                    SupportMessageDto last = msgs.isEmpty() ? null : SupportMessageDto.fromDomain(msgs.get(msgs.size() - 1));
                    return SupportConversationDto.fromDomain(c, unread, last);
                })
                .collect(Collectors.toList());
    }

    public AdminSupportConversationDetailDto adminGetConversationDetail(Long adminId, String conversationId, String ipAddress) {
        SupportConversation conv = supportStore.findConversationById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        // Audit View Action
        if (auditService != null) {
            String auditDetail = String.format("{\"action\":\"VIEW\",\"conversationId\":\"%s\",\"userId\":%s}", conv.id(), conv.userId());
            auditService.log(adminId, "SUPPORT_VIEW_CONVERSATION", "SUPPORT_CONVERSATION", toEntityId(conv.id()), auditDetail, ipAddress);
        }

        // Mark messages read by agent
        supportStore.markMessagesRead(conversationId, MessageSender.AGENT, Instant.now());

        List<SupportMessageDto> messageDtos = supportStore.findMessagesByConversationId(conversationId, null).stream()
                .map(SupportMessageDto::fromDomain)
                .collect(Collectors.toList());

        // Resolve attached transaction details (only if attached by reference, else masked summary)
        MaskedTransactionSummaryDto txSummary = null;
        if (conv.attachedTransactionRef() != null && !conv.attachedTransactionRef().isBlank()) {
            txSummary = resolveTransactionSummary(conv.attachedTransactionRef(), true);
        }

        SupportMessageDto last = messageDtos.isEmpty() ? null : messageDtos.get(messageDtos.size() - 1);
        SupportConversationDto convDto = SupportConversationDto.fromDomain(conv, 0, last);

        return new AdminSupportConversationDetailDto(
                convDto,
                messageDtos,
                txSummary,
                FaqBotEngine.SAFETY_WARNING
        );
    }

    public SupportMessageDto adminReply(Long adminId, String conversationId, AdminSupportReplyRequest request, String ipAddress) {
        SupportConversation conv = supportStore.findConversationById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        // Mask text before saving
        String maskedText = DataMaskingFilter.mask(request.text());

        Instant now = Instant.now();
        String msgId = "msg_" + messageIdSeq.incrementAndGet();
        SupportMessage agentMsg = new SupportMessage(
                msgId,
                conversationId,
                MessageSender.AGENT,
                maskedText,
                now,
                null,
                null
        );
        supportStore.saveMessage(agentMsg);

        // Update status: if was WAITING, change to OPEN
        if (conv.status() == ConversationStatus.WAITING) {
            conv = conv.withStatus(ConversationStatus.OPEN);
            supportStore.saveConversation(conv);
        }

        // Audit Reply Action
        if (auditService != null) {
            String auditDetail = String.format("{\"action\":\"REPLY\",\"conversationId\":\"%s\",\"messageId\":\"%s\"}", conv.id(), msgId);
            auditService.log(adminId, "SUPPORT_REPLY", "SUPPORT_CONVERSATION", toEntityId(conv.id()), auditDetail, ipAddress);
        }

        // Send privacy-safe notification to user
        if (notificationStore != null) {
            try {
                notificationStore.saveNotification(new NotificationRecord(
                        conv.userId(),
                        NotificationChannelType.IN_APP,
                        NotificationType.GENERAL,
                        "Support Update",
                        "You have a new reply"
                ));
            } catch (Exception e) {
                log.warn("Could not send notification for support reply: {}", e.getMessage());
            }
        }

        return SupportMessageDto.fromDomain(agentMsg);
    }

    public SupportConversationDto adminResolve(Long adminId, String conversationId, String ipAddress) {
        SupportConversation conv = supportStore.findConversationById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        conv = conv.withStatus(ConversationStatus.RESOLVED);
        supportStore.saveConversation(conv);

        // Audit Resolve Action
        if (auditService != null) {
            String auditDetail = String.format("{\"action\":\"RESOLVE\",\"conversationId\":\"%s\"}", conv.id());
            auditService.log(adminId, "SUPPORT_RESOLVE", "SUPPORT_CONVERSATION", toEntityId(conv.id()), auditDetail, ipAddress);
        }

        String msgId = "msg_" + messageIdSeq.incrementAndGet();
        SupportMessage sysMsg = new SupportMessage(
                msgId,
                conversationId,
                MessageSender.AGENT,
                "Our support agent marked this conversation as resolved. If you need further help, please feel free to send a new message.",
                Instant.now(),
                null,
                null
        );
        supportStore.saveMessage(sysMsg);

        return SupportConversationDto.fromDomain(conv, 0, SupportMessageDto.fromDomain(sysMsg));
    }

    public List<CannedReplyDto> getCannedReplies() {
        return List.of(
                new CannedReplyDto("cr_1", "Checking with clearing network",
                        "We are checking with the LankaPay clearing network and will update you shortly."),
                new CannedReplyDto("cr_2", "Transaction verified",
                        "Your transaction has been verified. The funds should reflect in your account within 10 minutes."),
                new CannedReplyDto("cr_3", "Check statement",
                        "Please verify your recent statement in the Analytics tab for settled transactions."),
                new CannedReplyDto("cr_4", "Temporary card hold",
                        "For security reasons, your card has been placed on temporary hold. Please verify your details in the Cards tab."),
                new CannedReplyDto("cr_5", "Issue resolved",
                        "Your issue has been resolved. Please let us know if you have any further questions!")
        );
    }

    private MaskedTransactionSummaryDto resolveTransactionSummary(String reference, boolean attachedByCustomer) {
        if (transactionStore != null) {
            Optional<TransactionRecord> txOpt = transactionStore.findTransferByReference(reference);
            if (txOpt.isPresent()) {
                TransactionRecord tx = txOpt.get();
                String senderMasked = maskPhoneOrAccount(tx.senderPhone());
                String receiverMasked = maskPhoneOrAccount(tx.receiverPhone());
                return new MaskedTransactionSummaryDto(
                        tx.reference(),
                        tx.amount(),
                        tx.currency() != null ? tx.currency() : "LKR",
                        tx.status() != null ? tx.status().name() : "COMPLETED",
                        senderMasked,
                        receiverMasked,
                        tx.createdAt() != null ? tx.createdAt() : Instant.now(),
                        attachedByCustomer
                );
            }
        }

        // Fallback simulated summary
        return new MaskedTransactionSummaryDto(
                reference,
                new BigDecimal("2500.00"),
                "LKR",
                "COMPLETED",
                "+94 77 •••• 123",
                "+94 71 •••• 889",
                Instant.now().minusSeconds(3600),
                attachedByCustomer
        );
    }

    private String maskPhoneOrAccount(String val) {
        if (val == null || val.length() < 6) return "•••• ••••";
        return val.substring(0, 4) + " •••• " + val.substring(val.length() - 3);
    }
}
