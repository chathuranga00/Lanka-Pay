package com.lankapay.story.web.dto;

import com.lankapay.story.domain.StoryAudience;

import java.time.Instant;
import java.util.List;

/**
 * Request payload for editing an existing story.
 */
public record UpdateStoryRequest(
        String title,
        String caption,
        String imageOrGradient,
        List<StoryPageDto> contentPages,
        StoryAudience audience,
        Instant startsAt,
        Instant endsAt,
        Integer priority
) {
}
