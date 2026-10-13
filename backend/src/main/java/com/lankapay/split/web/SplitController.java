package com.lankapay.split.web;

import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.split.service.SplitService;
import com.lankapay.split.web.dto.CreateSplitRequest;
import com.lankapay.split.web.dto.PaySplitRequest;
import com.lankapay.split.web.dto.SplitResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Bill Splits", description = "Group bill splitting, participant payment requests, and reminder management")
@com.lankapay.feature.annotation.FeatureToggle("split")
public class SplitController {

    private final SplitService splitService;
    private final UserStore userStore;

    public SplitController(SplitService splitService, UserStore userStore) {
        this.splitService = splitService;
        this.userStore = userStore;
    }

    @PostMapping("/api/v1/splits")
    @Operation(summary = "Create a new bill split")
    public ResponseEntity<SplitResponse> createSplit(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @Valid @RequestBody CreateSplitRequest request
    ) {
        User user = resolveUser(principal);
        SplitResponse response = splitService.createSplit(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/v1/splits")
    @Operation(summary = "List all bill splits for the current user (as owner or participant)")
    public ResponseEntity<List<SplitResponse>> listSplits(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal
    ) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(splitService.listUserSplits(user.getId()));
    }

    @GetMapping({"/api/v1/splits/{id}", "/splits/{id}"})
    @Operation(summary = "Get bill split details by ID")
    public ResponseEntity<SplitResponse> getSplit(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @PathVariable String id
    ) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(splitService.getSplit(id, user.getId()));
    }

    @PostMapping({"/api/v1/splits/{id}/cancel", "/splits/{id}/cancel"})
    @Operation(summary = "Cancel unpaid shares / cancel bill split by owner")
    public ResponseEntity<SplitResponse> cancelSplit(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @PathVariable String id
    ) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(splitService.cancelSplit(id, user.getId()));
    }

    @PostMapping({"/api/v1/splits/{id}/decline", "/splits/{id}/decline"})
    @Operation(summary = "Participant declines their split share")
    public ResponseEntity<SplitResponse> declineShare(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @PathVariable String id
    ) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(splitService.declineShare(id, user.getId()));
    }

    @PostMapping({"/api/v1/splits/{id}/remind/{participantId}", "/splits/{id}/remind/{participantId}"})
    @Operation(summary = "Send reminder to an unpaid participant (rate limited to 1 per 24 hours)")
    public ResponseEntity<SplitResponse> remindParticipant(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @PathVariable String id,
            @PathVariable Long participantId
    ) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(splitService.remindParticipant(id, participantId, user.getId()));
    }

    @PostMapping({"/api/v1/splits/{id}/pay", "/splits/{id}/pay"})
    @Operation(summary = "Participant pays their share of the split")
    public ResponseEntity<SplitResponse> payShare(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @PathVariable String id,
            @RequestBody(required = false) PaySplitRequest payRequest
    ) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(splitService.payShare(id, user.getId(), payRequest));
    }

    private User resolveUser(org.springframework.security.core.userdetails.User principal) {
        return userStore.findUserByPhone(principal.getUsername())
                .map(acc -> {
                    User u = new User(acc.phone(), acc.fullName(), acc.nicNumber(), acc.passwordHash());
                    u.setId(acc.id());
                    u.setStatus(acc.status());
                    u.setRole(acc.role());
                    return u;
                })
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + principal.getUsername()));
    }
}
