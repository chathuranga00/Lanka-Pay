package com.lankapay.story.web.dto;

import com.lankapay.story.domain.Story;
import com.lankapay.story.domain.StoryAudience;
import com.lankapay.story.domain.StoryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request payload for creating a new story via Admin API.
 */
public record CreateStoryRequest(
        String id,
        @NotBlank(message = "Title is required")
        String title,
        @NotBlank(message = "Caption is required")
        String caption,
        String imageOrGradient,
        @NotEmpty(message = "At least one content page is required")
        List<StoryPageDto> contentPages,
        StoryAudience audience,
        Instant startsAt,
        Instant endsAt,
        Integer priority,
        Boolean publishNow
) {
    public Story toDomain() {
        String storyId = (id != null && !id.isBlank())
                ? id.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "-")
                : "story-" + UUID.randomUUID().toString().substring(0, 8);

        return new Story(
                storyId,
                title.trim(),
                caption.trim(),
                imageOrGradient != null ? imageOrGradient.trim() : "gradient:linear(135deg, #4F46E5, #3B82F6)",
                contentPages != null ? contentPages.stream().map(StoryPageDto::toDomain).toList() : List.of(),
                audience != null ? audience : StoryAudience.ALL,
                startsAt,
                endsAt,
                priority != null ? priority : 100,
                Boolean.TRUE.equals(publishNow) ? StoryStatus.PUBLISHED : StoryStatus.DRAFT,
                0,
                Instant.now(),
                Instant.now()
        );
    }
}
