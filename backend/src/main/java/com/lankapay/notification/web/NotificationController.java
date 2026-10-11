package com.lankapay.notification.web;

import com.lankapay.common.web.ErrorResponse;
import com.lankapay.notification.domain.NotificationType;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.notification.web.dto.NotificationPreferenceResponse;
import com.lankapay.notification.web.dto.NotificationResponse;
import com.lankapay.notification.web.dto.UpdateNotificationPreferenceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "User notifications and channel preferences")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // ── GET /notifications ─────────────────────────────────────

    @Operation(summary = "Get user notifications",
               description = "Returns paginated stored notifications for the authenticated user.")
    @ApiResponse(responseCode = "200", description = "Paginated notifications")
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Long userId) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
        Page<NotificationResponse> result = notificationService.getUserNotifications(userId, unreadOnly, type, pageable);
        return ResponseEntity.ok(result);
    }

    // ── PATCH /notifications/{id}/read ─────────────────────────

    @Operation(summary = "Mark notification as read",
               description = "Marks a specific notification as read. Object-level check: caller must own notification.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Notification marked as read"),
        @ApiResponse(responseCode = "404", description = "Notification not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {

        NotificationResponse response = notificationService.markAsRead(id, userId);
        return ResponseEntity.ok(response);
    }

    // ── POST /notifications/mark-all-read ──────────────────────

    @Operation(summary = "Mark all notifications as read",
               description = "Marks all unread notifications of the authenticated user as read.")
    @ApiResponse(responseCode = "200", description = "Count of notifications updated")
    @PostMapping("/mark-all-read")
    public ResponseEntity<Map<String, Object>> markAllAsRead(@AuthenticationPrincipal Long userId) {
        int updated = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read", "updatedCount", updated));
    }

    // ── GET /notifications/preferences ─────────────────────────

    @Operation(summary = "Get notification preferences",
               description = "Returns channel and alert preferences for the authenticated user.")
    @ApiResponse(responseCode = "200", description = "Current preferences")
    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> getPreferences(@AuthenticationPrincipal Long userId) {
        NotificationPreferenceResponse response = notificationService.getPreferences(userId);
        return ResponseEntity.ok(response);
    }

    // ── PATCH /notifications/preferences ───────────────────────

    @Operation(summary = "Update notification preferences",
               description = "Enables or disables notification channels (in-app, push, email, SMS) and alert types.")
    @ApiResponse(responseCode = "200", description = "Updated preferences")
    @PatchMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> updatePreferences(
            @Valid @RequestBody UpdateNotificationPreferenceRequest request,
            @AuthenticationPrincipal Long userId) {

        NotificationPreferenceResponse response = notificationService.updatePreferences(userId, request);
        return ResponseEntity.ok(response);
    }
}
