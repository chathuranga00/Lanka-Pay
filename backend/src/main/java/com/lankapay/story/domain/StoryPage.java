package com.lankapay.story.domain;

/**
 * A single slide/page within a story.
 */
public record StoryPage(
        String text,
        String imageRef,
        String actionLabel,
        String deepLink
) {
    public StoryPage {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Story page text must not be empty");
        }
    }
}
