package com.lankapay.story.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Domain entity representing an in-app interactive story.
 */
public record Story(
        String id,
        String title,
        String caption,
        String imageOrGradient,
        List<StoryPage> contentPages,
        StoryAudience audience,
        Instant startsAt,
        Instant endsAt,
        int priority,
        StoryStatus status,
        long actionClickCount,
        Instant createdAt,
        Instant updatedAt
) {
    public Story {
        contentPages = contentPages != null ? Collections.unmodifiableList(new ArrayList<>(contentPages)) : List.of();
        audience = audience != null ? audience : StoryAudience.ALL;
        status = status != null ? status : StoryStatus.DRAFT;
        createdAt = createdAt != null ? createdAt : Instant.now();
        updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public boolean isActiveAt(Instant now) {
        if (status != StoryStatus.PUBLISHED) {
            return false;
        }
        if (startsAt != null && now.isBefore(startsAt)) {
            return false;
        }
        if (endsAt != null && now.isAfter(endsAt)) {
            return false;
        }
        return true;
    }

    public Story withStatus(StoryStatus newStatus) {
        return new Story(
                this.id,
                this.title,
                this.caption,
                this.imageOrGradient,
                this.contentPages,
                this.audience,
                this.startsAt,
                this.endsAt,
                this.priority,
                newStatus,
                this.actionClickCount,
                this.createdAt,
                Instant.now()
        );
    }

    public Story withIncrementedClickCount() {
        return new Story(
                this.id,
                this.title,
                this.caption,
                this.imageOrGradient,
                this.contentPages,
                this.audience,
                this.startsAt,
                this.endsAt,
                this.priority,
                this.status,
                this.actionClickCount + 1,
                this.createdAt,
                Instant.now()
        );
    }

    public Story withUpdates(
            String title,
            String caption,
            String imageOrGradient,
            List<StoryPage> contentPages,
            StoryAudience audience,
            Instant startsAt,
            Instant endsAt,
            int priority
    ) {
        return new Story(
                this.id,
                title != null ? title : this.title,
                caption != null ? caption : this.caption,
                imageOrGradient != null ? imageOrGradient : this.imageOrGradient,
                contentPages != null ? contentPages : this.contentPages,
                audience != null ? audience : this.audience,
                startsAt != null ? startsAt : this.startsAt,
                endsAt != null ? endsAt : this.endsAt,
                priority,
                this.status,
                this.actionClickCount,
                this.createdAt,
                Instant.now()
        );
    }
}
