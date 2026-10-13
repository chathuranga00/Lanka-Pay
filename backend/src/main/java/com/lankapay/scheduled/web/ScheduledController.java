package com.lankapay.scheduled.web;

import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.scheduled.domain.ScheduledPayment;
import com.lankapay.scheduled.domain.ScheduledRunRecord;
import com.lankapay.scheduled.service.ScheduledPaymentService;
import com.lankapay.scheduled.web.dto.CreateScheduleRequest;
import com.lankapay.scheduled.web.dto.ScheduleResponse;
import com.lankapay.scheduled.web.dto.ScheduleRunResponse;
import com.lankapay.scheduled.web.dto.UpdateScheduleRequest;
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
@Tag(name = "Scheduled Payments", description = "Recurring and scheduled transfers and bill payments")
public class ScheduledController {

    private final ScheduledPaymentService scheduledPaymentService;
    private final UserStore userStore;

    public ScheduledController(ScheduledPaymentService scheduledPaymentService, UserStore userStore) {
        this.scheduledPaymentService = scheduledPaymentService;
        this.userStore = userStore;
    }

    @PostMapping("/api/v1/scheduled")
    @Operation(summary = "Create scheduled payment with step-up auth and consent")
    public ResponseEntity<ScheduleResponse> createSchedule(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @RequestHeader(value = "X-Step-Up-Pin", required = false) String pinHeader,
            @Valid @RequestBody CreateScheduleRequest request
    ) {
        User user = resolveUser(principal);
        ScheduledPayment schedule = scheduledPaymentService.createSchedule(user, request, pinHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(ScheduleResponse.from(schedule));
    }

    @GetMapping("/api/v1/scheduled")
    @Operation(summary = "List all scheduled payments for current user")
    public ResponseEntity<List<ScheduleResponse>> listSchedules(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal
    ) {
        User user = resolveUser(principal);
        List<ScheduleResponse> list = scheduledPaymentService.listUserSchedules(user)
                .stream()
                .map(ScheduleResponse::from)
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/api/v1/scheduled/{id}")
    @Operation(summary = "Get scheduled payment details by ID")
    public ResponseEntity<ScheduleResponse> getSchedule(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @PathVariable Long id
    ) {
        User user = resolveUser(principal);
        ScheduledPayment schedule = scheduledPaymentService.getScheduleById(user, id);
        return ResponseEntity.ok(ScheduleResponse.from(schedule));
    }

    @PatchMapping("/api/v1/scheduled/{id}")
    @Operation(summary = "Update, pause, or resume scheduled payment")
    public ResponseEntity<ScheduleResponse> updateSchedule(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @PathVariable Long id,
            @RequestHeader(value = "X-Step-Up-Pin", required = false) String pinHeader,
            @Valid @RequestBody UpdateScheduleRequest request
    ) {
        User user = resolveUser(principal);
        ScheduledPayment updated = scheduledPaymentService.updateSchedule(user, id, request, pinHeader);
        return ResponseEntity.ok(ScheduleResponse.from(updated));
    }

    @DeleteMapping("/api/v1/scheduled/{id}")
    @Operation(summary = "Cancel scheduled payment")
    public ResponseEntity<ScheduleResponse> cancelSchedule(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @PathVariable Long id
    ) {
        User user = resolveUser(principal);
        ScheduledPayment cancelled = scheduledPaymentService.cancelSchedule(user, id);
        return ResponseEntity.ok(ScheduleResponse.from(cancelled));
    }

    @GetMapping({"/api/v1/scheduled/{id}/runs", "/scheduled/{id}/runs"})
    @Operation(summary = "Get run history for scheduled payment")
    public ResponseEntity<List<ScheduleRunResponse>> getRuns(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
            @PathVariable Long id
    ) {
        User user = resolveUser(principal);
        List<ScheduleRunResponse> runs = scheduledPaymentService.getScheduleRuns(user, id)
                .stream()
                .map(ScheduleRunResponse::from)
                .toList();
        return ResponseEntity.ok(runs);
    }

    private User resolveUser(org.springframework.security.core.userdetails.User principal) {
        return userStore.findUserByPhone(principal.getUsername())
                .map(acc -> {
                    User u = new User(acc.phone(), acc.fullName(), null, null);
                    u.setId(acc.id());
                    u.setPasswordHash(acc.passwordHash());
                    return u;
                })
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + principal.getUsername()));
    }
}
