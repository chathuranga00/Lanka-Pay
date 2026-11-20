package com.lankapay.support.web;

import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.support.service.SupportService;
import com.lankapay.support.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Support", description = "Customer support conversations, messages, FAQ bot, and inquiry resolution")
@com.lankapay.feature.annotation.FeatureToggle("support")
public class SupportController {

    private final SupportService supportService;
    private final UserStore userStore;

    @Autowired
    public SupportController(SupportService supportService, @Autowired(required = false) UserStore userStore) {
        this.supportService = supportService;
        this.userStore = userStore;
    }

    private Long resolveUserId(Object principal, Long queryUserId) {
        if (principal instanceof org.springframework.security.core.userdetails.User u) {
            if (userStore != null) {
                return userStore.findUserByPhone(u.getUsername())
                        .map(UserAccount::id)
                        .orElse(null);
            }
        } else if (principal instanceof Long l) {
            return l;
        } else if (principal instanceof User user) {
            return user.getId();
        }
        return queryUserId;
    }

    @PostMapping({"/api/v1/support/conversations", "/support/conversations"})
    @Operation(summary = "Start a support conversation with initial inquiry")
    public ResponseEntity<SupportConversationDto> createConversation(
            @Valid @RequestBody CreateConversationRequest request,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long resolvedId = resolveUserId(principal, userId);
        if (resolvedId == null) resolvedId = 1L; // fallback for test / simulated contexts
        return ResponseEntity.ok(supportService.createConversation(resolvedId, request));
    }

    @GetMapping({"/api/v1/support/conversations", "/support/conversations"})
    @Operation(summary = "List all support conversations for current user")
    public ResponseEntity<List<SupportConversationDto>> listConversations(
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long resolvedId = resolveUserId(principal, userId);
        if (resolvedId == null) resolvedId = 1L;
        return ResponseEntity.ok(supportService.listUserConversations(resolvedId));
    }

    @GetMapping({"/api/v1/support/conversations/{id}", "/support/conversations/{id}"})
    @Operation(summary = "Get single support conversation details")
    public ResponseEntity<SupportConversationDto> getConversation(
            @PathVariable String id,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long resolvedId = resolveUserId(principal, userId);
        if (resolvedId == null) resolvedId = 1L;
        return ResponseEntity.ok(supportService.getUserConversation(resolvedId, id));
    }

    @GetMapping({"/api/v1/support/conversations/{id}/messages", "/support/conversations/{id}/messages"})
    @Operation(summary = "Get messages for conversation (supports polling via ?after=)")
    public ResponseEntity<List<SupportMessageDto>> getMessages(
            @PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant after,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long resolvedId = resolveUserId(principal, userId);
        if (resolvedId == null) resolvedId = 1L;
        return ResponseEntity.ok(supportService.getMessages(resolvedId, id, after));
    }

    @PostMapping({"/api/v1/support/conversations/{id}/messages", "/support/conversations/{id}/messages"})
    @Operation(summary = "Send a message to support conversation")
    public ResponseEntity<SupportMessageDto> sendMessage(
            @PathVariable String id,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long resolvedId = resolveUserId(principal, userId);
        if (resolvedId == null) resolvedId = 1L;
        return ResponseEntity.ok(supportService.sendMessage(resolvedId, id, request));
    }

    @PostMapping({"/api/v1/support/conversations/{id}/resolve", "/support/conversations/{id}/resolve"})
    @Operation(summary = "Mark conversation resolved by user")
    public ResponseEntity<SupportConversationDto> resolveConversation(
            @PathVariable String id,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long resolvedId = resolveUserId(principal, userId);
        if (resolvedId == null) resolvedId = 1L;
        return ResponseEntity.ok(supportService.resolveConversation(resolvedId, id));
    }

    @GetMapping({"/api/v1/support/faq", "/support/faq"})
    @Operation(summary = "Get curated FAQ list")
    public ResponseEntity<List<FaqItemDto>> getFaqs() {
        return ResponseEntity.ok(supportService.getFaqs());
    }

    @GetMapping({"/api/v1/support/unread-count", "/support/unread-count"})
    @Operation(summary = "Get unread message badge count for current user")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long resolvedId = resolveUserId(principal, userId);
        if (resolvedId == null) resolvedId = 1L;
        return ResponseEntity.ok(Map.of("unreadCount", supportService.getUnreadCountForUser(resolvedId)));
    }
}
