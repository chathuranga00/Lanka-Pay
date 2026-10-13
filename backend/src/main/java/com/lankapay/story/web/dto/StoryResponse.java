package com.lankapay.story.web.dto;

import com.lankapay.story.domain.Story;
import com.lankapay.story.domain.StoryAudience;
import com.lankapay.story.domain.StoryStatus;

import java.time.Instant;
import java.util.List;

/**
 * Story response returned to mobile clients and admin portals.
 */
public record StoryResponse(
        String id,
        String title,
        String caption,
        String imageOrGradient,
        List<StoryPageDto> contentPages,
        StoryAudience audience,
        Instant startsAt,
        Instant endsAt,
        int priority,
        StoryStatus status,
        boolean seen,
        long actionClickCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static StoryResponse fromDomain(Story story, boolean seen) {
        return new StoryResponse(
                story.id(),
                story.title(),
                story.caption(),
                story.imageOrGradient(),
                story.contentPages().stream().map(StoryPageDto::fromDomain).toList(),
                story.audience(),
                story.startsAt(),
                story.endsAt(),
                story.priority(),
                story.status(),
                seen,
                story.actionClickCount(),
                story.createdAt(),
                story.updatedAt()
        );
    }
}
