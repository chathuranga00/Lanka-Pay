package com.lankapay.story.store;

import com.lankapay.story.domain.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory store for stories and user seen status.
 * Pre-seeded with 6 safe, educational sandbox stories.
 */
@Component
public class InMemoryStoryStore implements StoryStore {

    private final Map<String, Story> stories = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> userSeenStories = new ConcurrentHashMap<>();

    public InMemoryStoryStore() {
        seedInitialStories();
    }

    private void seedInitialStories() {
        // 1. Link your first account
        save(new Story(
                "story-link-account",
                "Link your first account",
                "Get started with LankaPay",
                "gradient:linear(135deg, #4F46E5, #3B82F6)",
                List.of(
                        new StoryPage(
                                "Connect your bank account to enable instant, zero-fee payments across Sri Lanka.",
                                "asset:account_link_guide",
                                "Link Account",
                                "/accounts/add"
                        )
                ),
                StoryAudience.ALL,
                null,
                null,
                10,
                StoryStatus.PUBLISHED,
                0,
                Instant.now(),
                Instant.now()
        ));

        // 2. How lookup protects your privacy
        save(new Story(
                "story-privacy-lookup",
                "How lookup protects your privacy",
                "Your data is secure",
                "gradient:linear(135deg, #10B981, #06B6D4)",
                List.of(
                        new StoryPage(
                                "We never expose your full bank account numbers to senders. Lookups use encrypted tokens only.",
                                "asset:privacy_shield",
                                "Privacy Settings",
                                "/settings/privacy"
                        )
                ),
                StoryAudience.ALL,
                null,
                null,
                20,
                StoryStatus.PUBLISHED,
                0,
                Instant.now(),
                Instant.now()
        ));

        // 3. Security tip: never share your OTP
        save(new Story(
                "story-security-otp",
                "Security tip: never share your OTP",
                "Stay safe from scams",
                "gradient:linear(135deg, #EF4444, #F59E0B)",
                List.of(
                        new StoryPage(
                                "LankaPay and bank staff will never ask for your 6-digit PIN, OTP, or password over phone, SMS, or email.",
                                "asset:security_warning",
                                "Security Center",
                                "/settings/security"
                        )
                ),
                StoryAudience.ALL,
                null,
                null,
                30,
                StoryStatus.PUBLISHED,
                0,
                Instant.now(),
                Instant.now()
        ));

        // 4. Try QR payments
        save(new Story(
                "story-qr-payments",
                "Try QR payments",
                "Scan & pay anywhere",
                "gradient:linear(135deg, #8B5CF6, #EC4899)",
                List.of(
                        new StoryPage(
                                "Pay effortlessly at over 400,000 LANKAQR merchants nationwide with zero convenience fees.",
                                "asset:qr_scan_demo",
                                "Scan LankaQR",
                                "/qr"
                        )
                ),
                StoryAudience.ALL,
                null,
                null,
                40,
                StoryStatus.PUBLISHED,
                0,
                Instant.now(),
                Instant.now()
        ));

        // 5. Set a monthly budget
        save(new Story(
                "story-monthly-budget",
                "Set a monthly budget",
                "Smart money habits",
                "gradient:linear(135deg, #0EA5E9, #2563EB)",
                List.of(
                        new StoryPage(
                                "Track your monthly spending limits and keep your finances in balance with real-time indicators.",
                                "asset:budget_tracker",
                                "View Activity",
                                "/history"
                        )
                ),
                StoryAudience.ALL,
                null,
                null,
                50,
                StoryStatus.PUBLISHED,
                0,
                Instant.now(),
                Instant.now()
        ));

        // 6. Rewards are simulated
        save(new Story(
                "story-rewards-simulated",
                "Rewards are simulated",
                "Test sandbox feature",
                "gradient:linear(135deg, #F59E0B, #8B5CF6)",
                List.of(
                        new StoryPage(
                                "Earn reward points with every payment in our sandbox environment. Discover partner offers risk-free.",
                                "asset:rewards_crown",
                                "Rewards Center",
                                "/rewards"
                        )
                ),
                StoryAudience.ALL,
                null,
                null,
                60,
                StoryStatus.PUBLISHED,
                0,
                Instant.now(),
                Instant.now()
        ));
    }

    @Override
    public Story save(Story story) {
        stories.put(story.id(), story);
        return story;
    }

    @Override
    public Optional<Story> findById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(stories.get(id));
    }

    @Override
    public List<Story> findAll() {
        return new ArrayList<>(stories.values());
    }

    @Override
    public void delete(String id) {
        if (id != null) {
            stories.remove(id);
        }
    }

    @Override
    public void markSeen(Long userId, String storyId) {
        if (userId == null || storyId == null) return;
        userSeenStories.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(storyId);
    }

    @Override
    public boolean isSeen(Long userId, String storyId) {
        if (userId == null || storyId == null) return false;
        Set<String> seen = userSeenStories.get(userId);
        return seen != null && seen.contains(storyId);
    }

    @Override
    public Set<String> getSeenStoryIds(Long userId) {
        if (userId == null) return Set.of();
        Set<String> seen = userSeenStories.get(userId);
        return seen != null ? Collections.unmodifiableSet(seen) : Set.of();
    }
}
