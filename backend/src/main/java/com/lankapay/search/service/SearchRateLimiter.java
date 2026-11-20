package com.lankapay.search.service;

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
 * In-memory rate limiter for the global search endpoint.
 * Enforces per-user and per-IP limits using token-bucket algorithm.
 */
@Component
public class SearchRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SearchRateLimiter.class);

    private final long userCapacity;
    private final long ipCapacity;

    private final ConcurrentHashMap<Long, Bucket> userBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();

    public SearchRateLimiter(
            @Value("${app.search.rate-limit-per-user-per-minute:30}") long userCapacity,
            @Value("${app.search.rate-limit-per-ip-per-minute:60}") long ipCapacity) {
        this.userCapacity = userCapacity;
        this.ipCapacity = ipCapacity;
    }

    /**
     * Consume one token from both the per-user and per-IP buckets.
     * Throws {@link RateLimitException} if either bucket is empty.
     */
    public void consume(Long userId, String ip) {
        Bucket userBucket = userBuckets.computeIfAbsent(userId, this::newUserBucket);
        Bucket ipBucket = ipBuckets.computeIfAbsent(ip, this::newIpBucket);

        if (!userBucket.tryConsume(1)) {
            log.warn("Search rate limit exceeded for userId={}", userId);
            throw new RateLimitException("SEARCH_RATE_LIMITED",
                    "Too many search requests. Maximum " + userCapacity + " per minute. Please slow down.");
        }

        if (!ipBucket.tryConsume(1)) {
            log.warn("Search IP rate limit exceeded for ip={}", maskIp(ip));
            throw new RateLimitException("SEARCH_IP_RATE_LIMITED",
                    "Too many search requests from this network. Please slow down.");
        }
    }

    private Bucket newUserBucket(Long userId) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(userCapacity)
                        .refillGreedy(userCapacity, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private Bucket newIpBucket(String ip) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(ipCapacity)
                        .refillGreedy(ipCapacity, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private static String maskIp(String ip) {
        if (ip == null) return "unknown";
        int last = ip.lastIndexOf('.');
        return last > 0 ? ip.substring(0, last) + ".***" : "***";
    }
}
