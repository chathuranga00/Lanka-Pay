package com.lankapay.story.web;

import com.lankapay.story.service.StoryService;
import com.lankapay.story.web.dto.CreateStoryRequest;
import com.lankapay.story.web.dto.StoryResponse;
import com.lankapay.story.web.dto.UpdateStoryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Administrative endpoints for managing Stories:
 * - GET /api/v1/admin/stories
 * - GET /api/v1/admin/stories/{id}
 * - POST /api/v1/admin/stories
 * - PUT /api/v1/admin/stories/{id}
 * - POST /api/v1/admin/stories/{id}/publish
 * - POST /api/v1/admin/stories/{id}/archive
 *
 * Restricted to ADMIN role only and audited.
 */
@Tag(name = "Admin Stories", description = "Admin story management, curation, publishing, and archiving")
@RestController
@RequestMapping("/api/v1/admin/stories")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStoryController {

    private final StoryService storyService;

    public AdminStoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    @Operation(summary = "List all stories (admin)", description = "Returns all stories in any status for administration.")
    @ApiResponse(responseCode = "200", description = "Stories returned")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @GetMapping
    public ResponseEntity<List<StoryResponse>> listStories(
            @AuthenticationPrincipal Long adminUserId,
            @RequestParam(value = "userId", required = false) Long queryUserId) {
        Long effectiveAdminId = adminUserId != null ? adminUserId : (queryUserId != null ? queryUserId : 9999L);
        return ResponseEntity.ok(storyService.getAllStoriesForAdmin());
    }

    @Operation(summary = "Get single story (admin)", description = "Returns story details by id.")
    @ApiResponse(responseCode = "200", description = "Story returned")
    @ApiResponse(responseCode = "404", description = "Story not found")
    @GetMapping("/{id}")
    public ResponseEntity<StoryResponse> getStory(
            @PathVariable("id") String id,
            @AuthenticationPrincipal Long adminUserId) {
        return ResponseEntity.ok(storyService.getStoryById(id));
    }

    @Operation(summary = "Create story", description = "Creates a new story with content pages and targeting.")
    @ApiResponse(responseCode = "201", description = "Story created")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @PostMapping
    public ResponseEntity<StoryResponse> createStory(
            @Valid @RequestBody CreateStoryRequest request,
            @AuthenticationPrincipal Long adminUserId,
            @RequestParam(value = "userId", required = false) Long queryUserId,
            HttpServletRequest httpRequest) {
        Long effectiveAdminId = adminUserId != null ? adminUserId : (queryUserId != null ? queryUserId : 9999L);
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "127.0.0.1";
        StoryResponse response = storyService.createStory(request, effectiveAdminId, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Update story", description = "Updates details and pages of an existing story.")
    @ApiResponse(responseCode = "200", description = "Story updated")
    @ApiResponse(responseCode = "404", description = "Story not found")
    @PutMapping("/{id}")
    public ResponseEntity<StoryResponse> updateStory(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateStoryRequest request,
            @AuthenticationPrincipal Long adminUserId,
            @RequestParam(value = "userId", required = false) Long queryUserId,
            HttpServletRequest httpRequest) {
        Long effectiveAdminId = adminUserId != null ? adminUserId : (queryUserId != null ? queryUserId : 9999L);
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "127.0.0.1";
        StoryResponse response = storyService.updateStory(id, request, effectiveAdminId, clientIp);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Publish story", description = "Changes story status to PUBLISHED.")
    @ApiResponse(responseCode = "200", description = "Story published")
    @ApiResponse(responseCode = "404", description = "Story not found")
    @PostMapping("/{id}/publish")
    public ResponseEntity<StoryResponse> publishStory(
            @PathVariable("id") String id,
            @AuthenticationPrincipal Long adminUserId,
            @RequestParam(value = "userId", required = false) Long queryUserId,
            HttpServletRequest httpRequest) {
        Long effectiveAdminId = adminUserId != null ? adminUserId : (queryUserId != null ? queryUserId : 9999L);
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "127.0.0.1";
        StoryResponse response = storyService.publishStory(id, effectiveAdminId, clientIp);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Archive story", description = "Changes story status to ARCHIVED.")
    @ApiResponse(responseCode = "200", description = "Story archived")
    @ApiResponse(responseCode = "404", description = "Story not found")
    @PostMapping("/{id}/archive")
    public ResponseEntity<StoryResponse> archiveStory(
            @PathVariable("id") String id,
            @AuthenticationPrincipal Long adminUserId,
            @RequestParam(value = "userId", required = false) Long queryUserId,
            HttpServletRequest httpRequest) {
        Long effectiveAdminId = adminUserId != null ? adminUserId : (queryUserId != null ? queryUserId : 9999L);
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "127.0.0.1";
        StoryResponse response = storyService.archiveStory(id, effectiveAdminId, clientIp);
        return ResponseEntity.ok(response);
    }
}
