package com.lankapay.story.web.dto;

/**
 * Analytics counter response for action clicks (no PII returned).
 */
public record StoryActionClickResponse(
        String storyId,
        long actionClickCount,
        boolean success
) {
}
