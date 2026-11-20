package com.lankapay.support.web;

import com.lankapay.support.domain.ConversationStatus;
import com.lankapay.support.service.SupportService;
import com.lankapay.support.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Admin Support", description = "Admin support inbox, message replies, and conversation resolution (audited)")
@PreAuthorize("hasRole('ADMIN')")
@com.lankapay.feature.annotation.FeatureToggle("support")
public class AdminSupportController {

    private final SupportService supportService;

    @Autowired
    public AdminSupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    private Long resolveAdminId(Object principal, Long queryUserId) {
        if (principal instanceof org.springframework.security.core.userdetails.User u) {
            return 9999L;
        } else if (principal instanceof Long l) {
            return l;
        }
        return queryUserId != null ? queryUserId : 9999L;
    }

    @GetMapping({"/api/v1/admin/support/conversations", "/support/admin/conversations"})
    @Operation(summary = "List all support conversations for admin inbox")
    public ResponseEntity<List<SupportConversationDto>> listConversations(
            @RequestParam(required = false) ConversationStatus status,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(supportService.adminListConversations(status, search));
    }

    @GetMapping({"/api/v1/admin/support/conversations/{id}", "/support/admin/conversations/{id}"})
    @Operation(summary = "Get conversation detail with messages and masked/attached transaction (audited)")
    public ResponseEntity<AdminSupportConversationDetailDto> getConversationDetail(
            @PathVariable String id,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long adminUserId,
            HttpServletRequest servletRequest
    ) {
        Long adminId = resolveAdminId(principal, adminUserId);
        String ip = servletRequest != null ? servletRequest.getRemoteAddr() : "127.0.0.1";
        return ResponseEntity.ok(supportService.adminGetConversationDetail(adminId, id, ip));
    }

    @PostMapping({"/api/v1/admin/support/conversations/{id}/reply", "/support/admin/conversations/{id}/reply"})
    @Operation(summary = "Send an agent reply to a customer support conversation (audited, sends notification)")
    public ResponseEntity<SupportMessageDto> replyConversation(
            @PathVariable String id,
            @Valid @RequestBody AdminSupportReplyRequest request,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long adminUserId,
            HttpServletRequest servletRequest
    ) {
        Long adminId = resolveAdminId(principal, adminUserId);
        String ip = servletRequest != null ? servletRequest.getRemoteAddr() : "127.0.0.1";
        return ResponseEntity.ok(supportService.adminReply(adminId, id, request, ip));
    }

    @PostMapping({"/api/v1/admin/support/conversations/{id}/resolve", "/support/admin/conversations/{id}/resolve"})
    @Operation(summary = "Mark conversation resolved by admin agent (audited)")
    public ResponseEntity<SupportConversationDto> resolveConversation(
            @PathVariable String id,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long adminUserId,
            HttpServletRequest servletRequest
    ) {
        Long adminId = resolveAdminId(principal, adminUserId);
        String ip = servletRequest != null ? servletRequest.getRemoteAddr() : "127.0.0.1";
        return ResponseEntity.ok(supportService.adminResolve(adminId, id, ip));
    }

    @GetMapping({"/api/v1/admin/support/canned-replies", "/support/admin/canned-replies"})
    @Operation(summary = "List quick canned replies for support agents")
    public ResponseEntity<List<CannedReplyDto>> getCannedReplies() {
        return ResponseEntity.ok(supportService.getCannedReplies());
    }
}
