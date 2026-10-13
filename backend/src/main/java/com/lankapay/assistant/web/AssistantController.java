package com.lankapay.assistant.web;

import com.lankapay.assistant.service.AssistantService;
import com.lankapay.assistant.web.dto.*;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "Assistant", description = "Privacy-first AI Assistant gateway for feature explanation and insights")
public class AssistantController {

    private final AssistantService assistantService;
    private final UserStore userStore;

    @Autowired
    public AssistantController(AssistantService assistantService, @Autowired(required = false) UserStore userStore) {
        this.assistantService = assistantService;
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
        return queryUserId != null ? queryUserId : 1L;
    }

    @GetMapping("/consent")
    @Operation(summary = "Check whether authenticated user has enabled the AI assistant")
    public ResponseEntity<AssistantConsentResponse> getConsent(
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long resolvedId = resolveUserId(principal, userId);
        return ResponseEntity.ok(assistantService.getConsent(resolvedId));
    }

    @PostMapping("/consent")
    @Operation(summary = "Update user consent toggle for the AI assistant")
    public ResponseEntity<AssistantConsentResponse> updateConsent(
            @Valid @RequestBody AssistantConsentRequest request,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long resolvedId = resolveUserId(principal, userId);
        return ResponseEntity.ok(assistantService.updateConsent(resolvedId, Boolean.TRUE.equals(request.consented())));
    }

    @PostMapping({"/ask", "/query"})
    @Operation(summary = "Ask the AI assistant an app capability or insight question")
    public ResponseEntity<AssistantQueryResponse> ask(
            @Valid @RequestBody AssistantQueryRequest request,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long resolvedId = resolveUserId(principal, userId);
        return ResponseEntity.ok(assistantService.ask(resolvedId, request));
    }

    @PostMapping("/feedback")
    @Operation(summary = "Submit helpfulness feedback (thumbs up / thumbs down) for an AI response")
    public ResponseEntity<Map<String, String>> submitFeedback(
            @Valid @RequestBody AssistantFeedbackRequest request,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        return ResponseEntity.ok(Map.of("status", "RECEIVED"));
    }
}
