package com.lankapay.story.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Storage contract for stories and per-user seen status.
 */
public interface StoryStore {

    /**
     * Save or update a story.
     */
    Story save(Story story);

    /**
     * Find a story by its unique identifier.
     */
    Optional<Story> findById(String id);

    /**
     * Return all stories regardless of status.
     */
    List<Story> findAll();

    /**
     * Delete a story by ID.
     */
    void delete(String id);

    /**
     * Mark a story as seen by a specific user.
     */
    void markSeen(Long userId, String storyId);

    /**
     * Check if a specific story has been seen by a user.
     */
    boolean isSeen(Long userId, String storyId);

    /**
     * Retrieve all story IDs seen by a user.
     */
    Set<String> getSeenStoryIds(Long userId);
}
