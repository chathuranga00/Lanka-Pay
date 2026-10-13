package com.lankapay.story.web.dto;

import com.lankapay.story.domain.StoryPage;

/**
 * Data transfer object for a single story page slide.
 */
public record StoryPageDto(
        String text,
        String imageRef,
        String actionLabel,
        String deepLink
) {
    public static StoryPageDto fromDomain(StoryPage page) {
        return new StoryPageDto(
                page.text(),
                page.imageRef(),
                page.actionLabel(),
                page.deepLink()
        );
    }

    public StoryPage toDomain() {
        return new StoryPage(
                this.text,
                this.imageRef,
                this.actionLabel,
                this.deepLink
        );
    }
}
