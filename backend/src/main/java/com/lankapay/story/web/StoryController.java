package com.lankapay.story.web;

import com.lankapay.story.service.StoryService;
import com.lankapay.story.web.dto.StoryActionClickResponse;
import com.lankapay.story.web.dto.StoryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Public/authenticated controller for in-app stories:
 * - GET /api/v1/stories
 * - POST /api/v1/stories/{id}/seen
 * - POST /api/v1/stories/{id}/action-click
 */
@RestController
@RequestMapping("/api/v1/stories")
public class StoryController {

    private final StoryService storyService;

    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    @GetMapping
    public ResponseEntity<List<StoryResponse>> getStories(
            @AuthenticationPrincipal Long userId,
            @RequestParam(value = "userId", required = false) Long queryUserId) {
        Long effectiveUserId = userId != null ? userId : (queryUserId != null ? queryUserId : 1L);
        List<StoryResponse> stories = storyService.getActiveStoriesForUser(effectiveUserId);
        return ResponseEntity.ok(stories);
    }

    @PostMapping("/{id}/seen")
    public ResponseEntity<Map<String, Object>> markStorySeen(
            @PathVariable("id") String id,
            @AuthenticationPrincipal Long userId,
            @RequestParam(value = "userId", required = false) Long queryUserId) {
        Long effectiveUserId = userId != null ? userId : (queryUserId != null ? queryUserId : 1L);
        storyService.markStorySeen(effectiveUserId, id);
        return ResponseEntity.ok(Map.of("success", true, "storyId", id, "seen", true));
    }

    @PostMapping("/{id}/action-click")
    public ResponseEntity<StoryActionClickResponse> recordActionClick(
            @PathVariable("id") String id) {
        StoryActionClickResponse response = storyService.recordActionClick(id);
        return ResponseEntity.ok(response);
    }
}
