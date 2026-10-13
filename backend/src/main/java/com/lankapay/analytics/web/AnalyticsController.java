package com.lankapay.analytics.web;

import com.lankapay.analytics.domain.StatementExportAudit;
import com.lankapay.analytics.domain.UserTransactionCategory;
import com.lankapay.analytics.service.AnalyticsService;
import com.lankapay.analytics.web.dto.*;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Analytics & Statements", description = "Spending breakdown, budgets, and bank statements")
@com.lankapay.feature.annotation.FeatureToggle("analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserStore userStore;

    public AnalyticsController(AnalyticsService analyticsService, UserStore userStore) {
        this.analyticsService = analyticsService;
        this.userStore = userStore;
    }

    @GetMapping("/api/v1/analytics/summary")
    @Operation(summary = "Get monthly spending and analytics summary")
    public ResponseEntity<AnalyticsSummaryResponse> getSummary(
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) String month
    ) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(analyticsService.getSummary(user.getId(), month));
    }

    @GetMapping("/api/v1/budgets")
    @Operation(summary = "Get monthly budget and used percentage")
    public ResponseEntity<BudgetResponse> getBudget(
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) String month
    ) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(analyticsService.getBudget(user.getId(), month));
    }

    @PostMapping("/api/v1/budgets")
    @Operation(summary = "Create or set monthly budget")
    public ResponseEntity<BudgetResponse> setBudget(
            @AuthenticationPrincipal Object principal,
            @Valid @RequestBody SetBudgetRequest request
    ) {
        User user = resolveUser(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(analyticsService.setBudget(user.getId(), request));
    }

    @PatchMapping("/api/v1/budgets")
    @Operation(summary = "Update monthly budget")
    public ResponseEntity<BudgetResponse> patchBudget(
            @AuthenticationPrincipal Object principal,
            @RequestBody SetBudgetRequest request
    ) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(analyticsService.patchBudget(user.getId(), request));
    }

    @PutMapping({"/api/v1/analytics/transactions/{id}/category", "/api/v1/analytics/transactions/category"})
    @Operation(summary = "Recategorise or recolour a transaction")
    public ResponseEntity<UserTransactionCategory> updateCategory(
            @PathVariable(required = false) Long id,
            @AuthenticationPrincipal Object principal,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        User user = resolveUser(principal);
        UpdateCategoryRequest effectiveReq = (id != null) ? request.withTransactionId(id) : request;
        return ResponseEntity.ok(analyticsService.updateTransactionCategory(user.getId(), effectiveReq));
    }

    @GetMapping({"/api/v1/analytics/transactions", "/api/v1/analytics/activity"})
    @Operation(summary = "List user activity items with filters")
    public ResponseEntity<List<ActivityItemDto>> listActivity(
            @AuthenticationPrincipal Object principal,
            @ModelAttribute StatementRequest filter
    ) {
        User user = resolveUser(principal);
        return ResponseEntity.ok(analyticsService.listActivity(user.getId(), filter != null ? filter : new StatementRequest(null, null, null, null, null, null, null, null, null)));
    }

    @PostMapping("/api/v1/statements")
    @Operation(summary = "Generate account statement PDF with short-lived download token")
    public ResponseEntity<StatementResponse> createStatement(
            @AuthenticationPrincipal Object principal,
            @RequestBody(required = false) StatementRequest request,
            HttpServletRequest httpRequest
    ) {
        User user = resolveUser(principal);
        String clientIp = httpRequest.getRemoteAddr();
        StatementRequest effectiveReq = request != null ? request : new StatementRequest(null, null, null, null, null, null, null, null, null);
        StatementResponse resp = analyticsService.exportStatement(user.getId(), effectiveReq, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping("/api/v1/statements/download/{token}")
    @Operation(summary = "Download generated statement PDF")
    public ResponseEntity<byte[]> downloadStatement(
            @PathVariable String token,
            @AuthenticationPrincipal Object principal
    ) {
        User user = resolveUser(principal);
        StatementExportAudit audit = analyticsService.downloadStatement(token, user.getId());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + audit.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(audit.pdfBytes());
    }

    private User resolveUser(Object principal) {
        if (principal instanceof org.springframework.security.core.userdetails.User u) {
            return userStore.findUserByPhone(u.getUsername())
                    .map(acc -> {
                        User user = new User(acc.phone(), acc.fullName(), null, null);
                        user.setId(acc.id());
                        return user;
                    })
                    .orElseThrow(() -> new IllegalStateException("User not found for phone: " + u.getUsername()));
        }
        if (principal instanceof Long id) {
            return userStore.findUserById(id)
                    .map(acc -> {
                        User user = new User(acc.phone(), acc.fullName(), null, null);
                        user.setId(acc.id());
                        return user;
                    })
                    .orElseThrow(() -> new IllegalStateException("User not found for id: " + id));
        }
        if (principal instanceof String s) {
            try {
                Long id = Long.parseLong(s);
                return userStore.findUserById(id)
                        .map(acc -> {
                            User user = new User(acc.phone(), acc.fullName(), null, null);
                            user.setId(acc.id());
                            return user;
                        })
                        .orElseGet(() -> userStore.findUserByPhone(s)
                                .map(acc -> {
                                    User user = new User(acc.phone(), acc.fullName(), null, null);
                                    user.setId(acc.id());
                                    return user;
                                })
                                .orElseThrow(() -> new IllegalStateException("User not found: " + s)));
            } catch (NumberFormatException nfe) {
                return userStore.findUserByPhone(s)
                        .map(acc -> {
                            User user = new User(acc.phone(), acc.fullName(), null, null);
                            user.setId(acc.id());
                            return user;
                        })
                        .orElseThrow(() -> new IllegalStateException("User not found for phone: " + s));
            }
        }
        throw new IllegalStateException("Unsupported principal type: " + (principal != null ? principal.getClass() : "null"));
    }
}
