package com.lankapay.assistant.service;

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
 * Per-user rate limiter for AI assistant requests.
 * Default: 10 queries per minute per user.
 */
@Component
public class AssistantRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AssistantRateLimiter.class);

    private final long capacity;
    private final ConcurrentHashMap<Long, Bucket> userBuckets = new ConcurrentHashMap<>();

    public AssistantRateLimiter(
            @Value("${app.assistant.rate-limit-per-user:10}") long capacity) {
        this.capacity = capacity;
    }

    public void checkLimit(Long userId) {
        if (userId == null) return;
        Bucket bucket = userBuckets.computeIfAbsent(userId, this::newUserBucket);
        if (!bucket.tryConsume(1)) {
            log.warn("Assistant rate limit exceeded for userId={}", userId);
            throw new RateLimitException("ASSISTANT_RATE_LIMITED",
                    "Assistant rate limit exceeded. You can send at most " + capacity + " queries per minute.");
        }
    }

    private Bucket newUserBucket(Long userId) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
