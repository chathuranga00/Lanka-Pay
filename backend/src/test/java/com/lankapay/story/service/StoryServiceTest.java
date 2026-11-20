package com.lankapay.story.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.card.domain.Card;
import com.lankapay.card.domain.CardKind;
import com.lankapay.card.domain.CardStatus;
import com.lankapay.card.domain.CardStore;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.story.domain.Story;
import com.lankapay.story.domain.StoryAudience;
import com.lankapay.story.domain.StoryPage;
import com.lankapay.story.domain.StoryStatus;
import com.lankapay.story.store.InMemoryStoryStore;
import com.lankapay.story.web.dto.CreateStoryRequest;
import com.lankapay.story.web.dto.StoryPageDto;
import com.lankapay.story.web.dto.StoryResponse;
import com.lankapay.story.web.dto.UpdateStoryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoryService & StoryValidator Unit Tests")
class StoryServiceTest {

    private InMemoryStoryStore storyStore;
    private StoryValidator storyValidator;

    @Mock
    private AuditService auditService;
    @Mock
    private UserStore userStore;
    @Mock
    private LinkedAccountStore linkedAccountStore;
    @Mock
    private CardStore cardStore;

    private StoryService storyService;

    @BeforeEach
    void setUp() {
        storyStore = new InMemoryStoryStore();
        storyValidator = new StoryValidator();
        storyService = new StoryService(
                storyStore,
                storyValidator,
                auditService,
                userStore,
                linkedAccountStore,
                cardStore
        );
    }

    @Test
    @DisplayName("Pre-seeded stories: verifies all 6 safe default stories are initialized")
    void preSeededStoriesArePresent() {
        List<StoryResponse> adminStories = storyService.getAllStoriesForAdmin();
        assertThat(adminStories).hasSize(6);

        assertThat(adminStories).extracting(StoryResponse::title)
                .contains(
                        "Link your first account",
                        "How lookup protects your privacy",
                        "Security tip: never share your OTP",
                        "Try QR payments",
                        "Set a monthly budget",
                        "Rewards are simulated"
                );
    }

    @Test
    @DisplayName("Audience filtering: ALL matches any user")
    void audienceAllMatches() {
        assertThat(storyService.matchesAudience(StoryAudience.ALL, 1L)).isTrue();
        assertThat(storyService.matchesAudience(StoryAudience.ALL, 999L)).isTrue();
    }

    @Test
    @DisplayName("Audience filtering: HAS_CARD and NO_CARD work based on user card holdings")
    void audienceHasCardAndNoCard() {
        Card activeCard = new Card(1L, 101L, "tok_123", "1111", 12, 28, "Visa", "Primary Card", CardKind.LINKED, CardStatus.ACTIVE, null);
        when(cardStore.findAllByUserId(1L)).thenReturn(List.of(activeCard));
        when(cardStore.findAllByUserId(2L)).thenReturn(List.of());

        assertThat(storyService.matchesAudience(StoryAudience.HAS_CARD, 1L)).isTrue();
        assertThat(storyService.matchesAudience(StoryAudience.HAS_CARD, 2L)).isFalse();

        assertThat(storyService.matchesAudience(StoryAudience.NO_CARD, 1L)).isFalse();
        assertThat(storyService.matchesAudience(StoryAudience.NO_CARD, 2L)).isTrue();
    }

    @Test
    @DisplayName("Audience filtering: HAS_BANK_ACCOUNT and NO_BANK_ACCOUNT work correctly")
    void audienceBankAccountTargeting() {
        LinkedAccount activeAccount = new LinkedAccount(1L, "BOC", "hash", "1234", "Jane Doe", "ref");
        when(linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(1L)).thenReturn(List.of(activeAccount));
        when(linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(2L)).thenReturn(List.of());

        assertThat(storyService.matchesAudience(StoryAudience.HAS_BANK_ACCOUNT, 1L)).isTrue();
        assertThat(storyService.matchesAudience(StoryAudience.HAS_BANK_ACCOUNT, 2L)).isFalse();

        assertThat(storyService.matchesAudience(StoryAudience.NO_BANK_ACCOUNT, 1L)).isFalse();
        assertThat(storyService.matchesAudience(StoryAudience.NO_BANK_ACCOUNT, 2L)).isTrue();
    }

    @Test
    @DisplayName("Audience filtering: NEW_USERS checks registration date <= 7 days")
    void audienceNewUsersTargeting() {
        UserAccount newUser = new UserAccount(1L, "94771234567", "Jane Doe", "199012345678", "pass", UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now().minus(2, ChronoUnit.DAYS), Instant.now());
        UserAccount oldUser = new UserAccount(2L, "94779876543", "John Smith", "198512345678", "pass", UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now().minus(30, ChronoUnit.DAYS), Instant.now());

        when(userStore.findUserById(1L)).thenReturn(Optional.of(newUser));
        when(userStore.findUserById(2L)).thenReturn(Optional.of(oldUser));

        assertThat(storyService.matchesAudience(StoryAudience.NEW_USERS, 1L)).isTrue();
        assertThat(storyService.matchesAudience(StoryAudience.NEW_USERS, 2L)).isFalse();
    }

    @Test
    @DisplayName("Schedule window: inactive stories (future or expired) are excluded from user feed")
    void scheduleWindowFiltering() {
        Instant now = Instant.now();

        // 1. Future story
        Story futureStory = new Story(
                "future-story", "Future Story", "Caption", "gradient:test",
                List.of(new StoryPage("Text", null, null, null)),
                StoryAudience.ALL,
                now.plus(1, ChronoUnit.DAYS),
                now.plus(5, ChronoUnit.DAYS),
                10, StoryStatus.PUBLISHED, 0, now, now
        );
        storyStore.save(futureStory);

        // 2. Expired story
        Story expiredStory = new Story(
                "expired-story", "Expired Story", "Caption", "gradient:test",
                List.of(new StoryPage("Text", null, null, null)),
                StoryAudience.ALL,
                now.minus(10, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS),
                11, StoryStatus.PUBLISHED, 0, now, now
        );
        storyStore.save(expiredStory);

        List<StoryResponse> active = storyService.getActiveStoriesForUser(1L);
        assertThat(active).extracting(StoryResponse::id)
                .doesNotContain("future-story", "expired-story");
    }

    @Test
    @DisplayName("Per-user seen state: marking seen records seen flag without affecting other users")
    void perUserSeenState() {
        String storyId = "story-security-otp";

        List<StoryResponse> user1Before = storyService.getActiveStoriesForUser(1L);
        StoryResponse s1Before = user1Before.stream().filter(s -> s.id().equals(storyId)).findFirst().orElseThrow();
        assertThat(s1Before.seen()).isFalse();

        // Mark seen by user 1
        storyService.markStorySeen(1L, storyId);

        List<StoryResponse> user1After = storyService.getActiveStoriesForUser(1L);
        StoryResponse s1After = user1After.stream().filter(s -> s.id().equals(storyId)).findFirst().orElseThrow();
        assertThat(s1After.seen()).isTrue();

        // User 2 should still see it as unseen
        List<StoryResponse> user2Feed = storyService.getActiveStoriesForUser(2L);
        StoryResponse s2Feed = user2Feed.stream().filter(s -> s.id().equals(storyId)).findFirst().orElseThrow();
        assertThat(s2Feed.seen()).isFalse();
    }

    @Test
    @DisplayName("Action click: increments numerical counter without recording user PII")
    void actionClickIncrementsCounterWithoutPii() {
        String storyId = "story-link-account";
        var click1 = storyService.recordActionClick(storyId);
        assertThat(click1.storyId()).isEqualTo(storyId);
        assertThat(click1.actionClickCount()).isGreaterThanOrEqualTo(1);

        var click2 = storyService.recordActionClick(storyId);
        assertThat(click2.actionClickCount()).isEqualTo(click1.actionClickCount() + 1);
    }

    @Test
    @DisplayName("Validator: rejects HTML injection in titles, captions, and page texts")
    void validatorRejectsHtmlTags() {
        assertThatThrownBy(() -> storyValidator.validateTitleAndCaption("<script>alert(1)</script>", "Safe caption"))
                .isInstanceOf(com.lankapay.common.exception.ValidationException.class)
                .hasMessageContaining("forbidden HTML tags");

        assertThatThrownBy(() -> storyValidator.validateTitleAndCaption("Safe title", "Caption with <b>bold</b> tag"))
                .isInstanceOf(com.lankapay.common.exception.ValidationException.class)
                .hasMessageContaining("forbidden HTML tags");

        List<StoryPage> dangerousPages = List.of(new StoryPage("<img src=x onerror=alert(1)>", null, null, null));
        assertThatThrownBy(() -> storyValidator.validatePages(dangerousPages))
                .isInstanceOf(com.lankapay.common.exception.ValidationException.class)
                .hasMessageContaining("forbidden HTML tags");
    }

    @Test
    @DisplayName("Validator: rejects unsafe deep links and arbitrary external URLs")
    void validatorRejectsUnsafeDeepLinks() {
        // Arbitrary web URLs
        List<StoryPage> webUrlPages = List.of(new StoryPage("Plain text", null, "Click", "https://phishing.com/login"));
        assertThatThrownBy(() -> storyValidator.validatePages(webUrlPages))
                .isInstanceOf(com.lankapay.common.exception.ValidationException.class)
                .hasMessageContaining("Whitelisted deep-links only");

        // Javascript URI
        List<StoryPage> jsUrlPages = List.of(new StoryPage("Plain text", null, "Click", "javascript:evil()"));
        assertThatThrownBy(() -> storyValidator.validatePages(jsUrlPages))
                .isInstanceOf(com.lankapay.common.exception.ValidationException.class)
                .hasMessageContaining("Whitelisted deep-links only");

        // Valid whitelisted internal app links pass
        List<StoryPage> validPages = List.of(
                new StoryPage("Text 1", null, "Link", "/accounts/add"),
                new StoryPage("Text 2", null, "Settings", "lankapay://settings/security"),
                new StoryPage("Text 3", null, "Pay", "/qr")
        );
        storyValidator.validatePages(validPages); // No exception
    }

    @Test
    @DisplayName("Validator: rejects disallowed image hosts")
    void validatorRejectsDisallowedImageHosts() {
        List<StoryPage> evilImage = List.of(new StoryPage("Text", "https://malicious-cdn.com/bad.png", null, null));
        assertThatThrownBy(() -> storyValidator.validatePages(evilImage))
                .isInstanceOf(com.lankapay.common.exception.ValidationException.class)
                .hasMessageContaining("allowlisted asset store");

        List<StoryPage> validImage = List.of(
                new StoryPage("Text", "https://assets.lankapay.net/stories/banner.png", null, null),
                new StoryPage("Text", "asset:images/security.png", null, null),
                new StoryPage("Text", "gradient:linear(135deg, #10B981, #047857)", null, null)
        );
        storyValidator.validatePages(validImage); // No exception
    }

    @Test
    @DisplayName("Admin CRUD & Audit: create, update, publish, archive are properly audited")
    void adminOperationsAreAudited() {
        CreateStoryRequest createReq = new CreateStoryRequest(
                "new-story-1",
                "New Feature Announcement",
                "Explore our latest features",
                "gradient:linear(135deg, #6366F1, #8B5CF6)",
                List.of(new StoryPageDto("Page 1 text", null, "Explore", "/explore")),
                StoryAudience.ALL,
                null,
                null,
                50,
                false
        );

        StoryResponse created = storyService.createStory(createReq, 99L, "10.0.0.5");
        assertThat(created.id()).isEqualTo("new-story-1");
        assertThat(created.status()).isEqualTo(StoryStatus.DRAFT);
        verify(auditService).log(eq(99L), eq("STORY_CREATED"), eq("STORY"), isNull(), anyString(), eq("10.0.0.5"));

        // Update
        UpdateStoryRequest updateReq = new UpdateStoryRequest(
                "Updated Title",
                "Updated Caption",
                null,
                null,
                StoryAudience.ALL,
                null,
                null,
                40
        );
        StoryResponse updated = storyService.updateStory("new-story-1", updateReq, 99L, "10.0.0.5");
        assertThat(updated.title()).isEqualTo("Updated Title");
        verify(auditService).log(eq(99L), eq("STORY_UPDATED"), eq("STORY"), isNull(), anyString(), eq("10.0.0.5"));

        // Publish
        StoryResponse published = storyService.publishStory("new-story-1", 99L, "10.0.0.5");
        assertThat(published.status()).isEqualTo(StoryStatus.PUBLISHED);
        verify(auditService).log(eq(99L), eq("STORY_PUBLISHED"), eq("STORY"), isNull(), anyString(), eq("10.0.0.5"));

        // Archive
        StoryResponse archived = storyService.archiveStory("new-story-1", 99L, "10.0.0.5");
        assertThat(archived.status()).isEqualTo(StoryStatus.ARCHIVED);
        verify(auditService).log(eq(99L), eq("STORY_ARCHIVED"), eq("STORY"), isNull(), anyString(), eq("10.0.0.5"));
    }
}
