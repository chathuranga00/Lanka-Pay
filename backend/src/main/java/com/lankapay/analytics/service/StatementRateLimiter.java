package com.lankapay.analytics.service;

import com.lankapay.common.exception.RateLimitException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StatementRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(StatementRateLimiter.class);

    private final long userCapacity;
    private final ConcurrentHashMap<Long, Bucket> userBuckets = new ConcurrentHashMap<>();

    public StatementRateLimiter(
            @Value("${app.statement.rate-limit-per-user:5}") long userCapacity) {
        this.userCapacity = userCapacity;
    }

    public void consume(Long userId) {
        Bucket userBucket = userBuckets.computeIfAbsent(userId, this::newUserBucket);
        if (!userBucket.tryConsume(1)) {
            log.warn("Statement rate limit exceeded for userId={}", userId);
            throw new RateLimitException("STATEMENT_RATE_LIMITED",
                    "Statement export rate limit exceeded. You can generate at most " + userCapacity + " statements per 10 minutes.");
        }
    }

    private Bucket newUserBucket(Long userId) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(userCapacity)
                        .refillGreedy(userCapacity, Duration.ofMinutes(10))
                        .build())
                .build();
    }
}
