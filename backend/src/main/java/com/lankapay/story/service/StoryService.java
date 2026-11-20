package com.lankapay.story.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.card.domain.CardStore;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.story.domain.Story;
import com.lankapay.story.domain.StoryAudience;
import com.lankapay.story.domain.StoryStatus;
import com.lankapay.story.domain.StoryStore;
import com.lankapay.story.web.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Business service for managing stories, audience targeting, user seen states,
 * and audited admin operations.
 */
@Service
public class StoryService {

    private static final Logger log = LoggerFactory.getLogger(StoryService.class);

    private final StoryStore storyStore;
    private final StoryValidator storyValidator;
    private final AuditService auditService;
    private final UserStore userStore;
    private final LinkedAccountStore linkedAccountStore;
    private final CardStore cardStore;

    @Autowired
    public StoryService(
            StoryStore storyStore,
            StoryValidator storyValidator,
            AuditService auditService,
            @Autowired(required = false) UserStore userStore,
            @Autowired(required = false) LinkedAccountStore linkedAccountStore,
            @Autowired(required = false) CardStore cardStore
    ) {
        this.storyStore = storyStore;
        this.storyValidator = storyValidator;
        this.auditService = auditService;
        this.userStore = userStore;
        this.linkedAccountStore = linkedAccountStore;
        this.cardStore = cardStore;
    }

    /**
     * Retrieve all active, audience-matched stories for a given user, annotated with seen state.
     */
    public List<StoryResponse> getActiveStoriesForUser(Long userId) {
        Instant now = Instant.now();
        List<Story> all = storyStore.findAll();

        return all.stream()
                .filter(s -> s.isActiveAt(now))
                .filter(s -> matchesAudience(s.audience(), userId))
                .sorted(Comparator.comparingInt(Story::priority))
                .map(s -> StoryResponse.fromDomain(s, storyStore.isSeen(userId, s.id())))
                .toList();
    }

    /**
     * Audience filtering logic.
     */
    public boolean matchesAudience(StoryAudience audience, Long userId) {
        if (audience == null || audience == StoryAudience.ALL) {
            return true;
        }
        if (userId == null) {
            return false;
        }

        switch (audience) {
            case NEW_USERS -> {
                if (userStore == null) return true;
                Optional<UserAccount> userOpt = userStore.findUserById(userId);
                if (userOpt.isEmpty()) return false;
                UserAccount u = userOpt.get();
                // Considered new if registered within the last 7 days or not yet active
                if (u.createdAt() != null && Duration.between(u.createdAt(), Instant.now()).toDays() <= 7) {
                    return true;
                }
                return u.status() != null && !u.status().name().equals("ACTIVE");
            }
            case HAS_CARD -> {
                if (cardStore == null) return false;
                var cards = cardStore.findAllByUserId(userId);
                return cards != null && cards.stream().anyMatch(c -> !c.isRemoved());
            }
            case NO_CARD -> {
                if (cardStore == null) return true;
                var cards = cardStore.findAllByUserId(userId);
                return cards == null || cards.stream().noneMatch(c -> !c.isRemoved());
            }
            case HAS_BANK_ACCOUNT -> {
                if (linkedAccountStore == null) return false;
                var accts = linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(userId);
                return accts != null && accts.stream().anyMatch(a -> a.status() == BankAccountStatus.ACTIVE);
            }
            case NO_BANK_ACCOUNT -> {
                if (linkedAccountStore == null) return true;
                var accts = linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(userId);
                return accts == null || accts.stream().noneMatch(a -> a.status() == BankAccountStatus.ACTIVE);
            }
            default -> {
                return true;
            }
        }
    }

    /**
     * Mark story as seen by the user.
     */
    public void markStorySeen(Long userId, String storyId) {
        if (storyId == null || storyId.isBlank()) return;
        Story story = storyStore.findById(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("Story", storyId));

        storyStore.markSeen(userId, story.id());
    }

    /**
     * Increment action-click analytics counter without storing PII or IP.
     */
    public StoryActionClickResponse recordActionClick(String storyId) {
        Story story = storyStore.findById(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("Story", storyId));

        Story updated = story.withIncrementedClickCount();
        storyStore.save(updated);
        log.info("Action click recorded for storyId={} (clicks={})", storyId, updated.actionClickCount());

        return new StoryActionClickResponse(storyId, updated.actionClickCount(), true);
    }

    // ── Admin Operations (Audited) ──────────────────────────────────────────

    public List<StoryResponse> getAllStoriesForAdmin() {
        return storyStore.findAll().stream()
                .sorted(Comparator.comparingInt(Story::priority))
                .map(s -> StoryResponse.fromDomain(s, false))
                .toList();
    }

    public StoryResponse getStoryById(String id) {
        Story story = storyStore.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Story", id));
        return StoryResponse.fromDomain(story, false);
    }

    public StoryResponse createStory(CreateStoryRequest request, Long adminId, String clientIp) {
        storyValidator.validateTitleAndCaption(request.title(), request.caption());
        var pages = request.contentPages() != null
                ? request.contentPages().stream().map(StoryPageDto::toDomain).toList()
                : List.<com.lankapay.story.domain.StoryPage>of();
        storyValidator.validatePages(pages);

        Story story = request.toDomain();
        Story saved = storyStore.save(story);

        auditLog(adminId, "STORY_CREATED", saved.id(), "{\"title\":\"" + saved.title() + "\",\"status\":\"" + saved.status() + "\"}", clientIp);
        return StoryResponse.fromDomain(saved, false);
    }

    public StoryResponse updateStory(String id, UpdateStoryRequest request, Long adminId, String clientIp) {
        Story existing = storyStore.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Story", id));

        if (request.title() != null || request.caption() != null) {
            storyValidator.validateTitleAndCaption(
                    request.title() != null ? request.title() : existing.title(),
                    request.caption() != null ? request.caption() : existing.caption()
            );
        }

        var pages = request.contentPages() != null
                ? request.contentPages().stream().map(StoryPageDto::toDomain).toList()
                : existing.contentPages();
        storyValidator.validatePages(pages);

        Story updated = existing.withUpdates(
                request.title(),
                request.caption(),
                request.imageOrGradient(),
                pages,
                request.audience(),
                request.startsAt(),
                request.endsAt(),
                request.priority() != null ? request.priority() : existing.priority()
        );
        Story saved = storyStore.save(updated);

        auditLog(adminId, "STORY_UPDATED", saved.id(), "{\"title\":\"" + saved.title() + "\"}", clientIp);
        return StoryResponse.fromDomain(saved, false);
    }

    public StoryResponse publishStory(String id, Long adminId, String clientIp) {
        Story existing = storyStore.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Story", id));

        Story published = existing.withStatus(StoryStatus.PUBLISHED);
        Story saved = storyStore.save(published);

        auditLog(adminId, "STORY_PUBLISHED", saved.id(), "{\"status\":\"PUBLISHED\"}", clientIp);
        return StoryResponse.fromDomain(saved, false);
    }

    public StoryResponse archiveStory(String id, Long adminId, String clientIp) {
        Story existing = storyStore.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Story", id));

        Story archived = existing.withStatus(StoryStatus.ARCHIVED);
        Story saved = storyStore.save(archived);

        auditLog(adminId, "STORY_ARCHIVED", saved.id(), "{\"status\":\"ARCHIVED\"}", clientIp);
        return StoryResponse.fromDomain(saved, false);
    }

    private void auditLog(Long actorId, String eventType, String entityId, String detailJson, String ipAddress) {
        if (auditService != null) {
            String sanitizedIp = (ipAddress != null && !ipAddress.isBlank()) ? ipAddress : "127.0.0.1";
            auditService.log(
                    actorId,
                    eventType,
                    "STORY",
                    null,
                    detailJson,
                    sanitizedIp
            );
        }
    }
}
