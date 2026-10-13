package com.lankapay.goals.web;

import com.lankapay.goals.service.GoalService;
import com.lankapay.goals.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for simulated savings goals.
 * Pure internal simulated ledger only, clearly labelled SIMULATED (no real money is locked).
 */
@Tag(name = "Savings Goals", description = "Simulated savings ledger goals management")
@RestController
@RequestMapping("/api/v1/goals")
@com.lankapay.feature.annotation.FeatureToggle("goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @Operation(summary = "List user savings goals", description = "Returns active and completed simulated goals for user")
    @GetMapping
    public ResponseEntity<List<GoalDto>> listGoals(@AuthenticationPrincipal Long userId) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(goalService.listGoals(userId));
    }

    @Operation(summary = "Get goal details", description = "Returns single goal details and simulated ledger entries")
    @GetMapping("/{id}")
    public ResponseEntity<GoalDetailsDto> getGoal(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") String goalId
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(goalService.getGoal(userId, goalId));
    }

    @Operation(summary = "Create savings goal", description = "Creates a new simulated savings goal")
    @PostMapping
    public ResponseEntity<GoalDto> createGoal(
            @AuthenticationPrincipal Long userId,
            @RequestHeader(value = "X-Step-Up-Pin", required = false) String stepUpPinHeader,
            @RequestBody @Valid CreateGoalRequest request,
            HttpServletRequest httpRequest
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "127.0.0.1";
        GoalDto created = goalService.createGoal(userId, request, stepUpPinHeader, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "Update savings goal", description = "Updates goal parameters (target, name, deadline, status)")
    @PutMapping("/{id}")
    public ResponseEntity<GoalDto> updateGoal(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") String goalId,
            @RequestBody @Valid UpdateGoalRequest request,
            HttpServletRequest httpRequest
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "127.0.0.1";
        GoalDto updated = goalService.updateGoal(userId, goalId, request, clientIp);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Delete savings goal", description = "Removes a simulated goal")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGoal(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") String goalId,
            HttpServletRequest httpRequest
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "127.0.0.1";
        goalService.deleteGoal(userId, goalId, clientIp);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Contribute to goal", description = "Simulated deposit into savings goal ledger")
    @PostMapping("/{id}/contribute")
    public ResponseEntity<GoalTransactionResponse> contribute(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") String goalId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Step-Up-Pin", required = false) String stepUpPinHeader,
            @RequestBody @Valid GoalTransactionRequest request,
            HttpServletRequest httpRequest
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "127.0.0.1";
        GoalTransactionResponse response = goalService.contribute(userId, goalId, request, idempotencyKey, stepUpPinHeader, clientIp);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Withdraw from goal", description = "Simulated withdrawal from savings goal ledger")
    @PostMapping("/{id}/withdraw")
    public ResponseEntity<GoalTransactionResponse> withdraw(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") String goalId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Step-Up-Pin", required = false) String stepUpPinHeader,
            @RequestBody @Valid GoalTransactionRequest request,
            HttpServletRequest httpRequest
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "127.0.0.1";
        GoalTransactionResponse response = goalService.withdraw(userId, goalId, request, idempotencyKey, stepUpPinHeader, clientIp);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get goal ledger entries", description = "Lists append-only simulated transactions")
    @GetMapping("/{id}/entries")
    public ResponseEntity<List<GoalLedgerEntryDto>> getEntries(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") String goalId
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(goalService.getLedgerEntries(userId, goalId));
    }
}
