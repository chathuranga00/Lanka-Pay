package com.lankapay.card.service;

import com.lankapay.common.exception.RateLimitException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token bucket rate limiter for card reveal operations.
 */
@Component
public class CardRevealRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(CardRevealRateLimiter.class);

    private final long capacity;
    private final ConcurrentHashMap<Long, Bucket> buckets = new ConcurrentHashMap<>();

    public CardRevealRateLimiter(
            @Value("${app.cards.reveal-rate-limit-per-user-per-minute:5}") long capacity) {
        this.capacity = capacity;
    }

    public void checkLimit(Long userId) {
        Bucket bucket = buckets.computeIfAbsent(userId, this::newBucket);
        if (!bucket.tryConsume(1)) {
            log.warn("Card reveal rate limit exceeded for userId={}", userId);
            throw new RateLimitException("CARD_REVEAL_RATE_LIMITED",
                    "Too many card reveal requests. Please wait a minute before trying again.");
        }
    }

    private Bucket newBucket(Long userId) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
