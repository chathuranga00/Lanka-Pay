package com.lankapay.insights.web;

import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.insights.domain.InsightItem;
import com.lankapay.insights.domain.InsightType;
import com.lankapay.insights.service.InsightService;
import com.lankapay.insights.web.dto.DismissInsightRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/insights")
@Tag(name = "Insights", description = "Rule-based financial insights and user dismissal preferences")
public class InsightController {

    private final InsightService insightService;
    private final UserStore userStore;

    @Autowired
    public InsightController(InsightService insightService, @Autowired(required = false) UserStore userStore) {
        this.insightService = insightService;
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

    @GetMapping
    @Operation(summary = "Get at most 3 personalized, factual insights for the authenticated user")
    public ResponseEntity<List<InsightItem>> getInsights(
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long resolvedId = resolveUserId(principal, userId);
        List<InsightItem> insights = insightService.getInsights(resolvedId);
        return ResponseEntity.ok(insights);
    }

    @PostMapping("/dismiss")
    @Operation(summary = "Dismiss an insight type so it is no longer shown")
    public ResponseEntity<Map<String, Object>> dismissInsight(
            @Valid @RequestBody DismissInsightRequest request,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long resolvedId = resolveUserId(principal, userId);
        insightService.dismissType(resolvedId, request.type());
        return ResponseEntity.ok(Map.of(
                "status", "DISMISSED",
                "type", request.type().name(),
                "remainingInsights", insightService.getInsights(resolvedId)
        ));
    }

    @PostMapping("/{type}/dismiss")
    @Operation(summary = "Dismiss an insight type by path variable")
    public ResponseEntity<Map<String, Object>> dismissInsightByPath(
            @PathVariable InsightType type,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long resolvedId = resolveUserId(principal, userId);
        insightService.dismissType(resolvedId, type);
        return ResponseEntity.ok(Map.of(
                "status", "DISMISSED",
                "type", type.name(),
                "remainingInsights", insightService.getInsights(resolvedId)
        ));
    }

    @DeleteMapping("/{type}/dismiss")
    @Operation(summary = "Undismiss an insight type to restore it")
    public ResponseEntity<Map<String, Object>> undismissInsight(
            @PathVariable InsightType type,
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long resolvedId = resolveUserId(principal, userId);
        insightService.undismissType(resolvedId, type);
        return ResponseEntity.ok(Map.of(
                "status", "RESTORED",
                "type", type.name()
        ));
    }

    @GetMapping("/dismissed")
    @Operation(summary = "Get all dismissed insight types for user")
    public ResponseEntity<Set<InsightType>> getDismissedTypes(
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) Long userId
    ) {
        Long resolvedId = resolveUserId(principal, userId);
        return ResponseEntity.ok(insightService.getDismissedTypes(resolvedId));
    }
}
