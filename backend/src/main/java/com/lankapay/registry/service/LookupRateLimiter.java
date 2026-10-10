package com.lankapay.registry.service;

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
 * In-memory rate limiter for the payment identity lookup endpoint.
 *
 * Uses Bucket4j token-bucket algorithm with two layers:
 *   1. Per-user bucket  — keyed by authenticated userId
 *   2. Per-IP bucket    — keyed by caller IP address
 *
 * Both must pass for a request to proceed.
 *
 * The buckets are held in ConcurrentHashMaps and never evicted in this
 * prototype (acceptable for a university build; production would use Redis).
 *
 * No PII (phone numbers, names) is ever stored or logged here.
 */
@Component
public class LookupRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LookupRateLimiter.class);

    private final long userCapacity;
    private final long ipCapacity;

    private final ConcurrentHashMap<Long,   Bucket> userBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> ipBuckets   = new ConcurrentHashMap<>();

    public LookupRateLimiter(
            @Value("${app.lookup.rate-limit-per-user-per-minute:10}") long userCapacity,
            @Value("${app.lookup.rate-limit-per-ip-per-minute:20}")   long ipCapacity) {

        this.userCapacity = userCapacity;
        this.ipCapacity   = ipCapacity;
    }

    /**
     * Consume one token from both the per-user and per-IP buckets.
     * Throws {@link RateLimitException} if either bucket is empty.
     *
     * @param userId caller's authenticated user ID
     * @param ip     caller's IP address (already extracted by the controller)
     */
    public void consume(Long userId, String ip) {
        Bucket userBucket = userBuckets.computeIfAbsent(userId,   this::newUserBucket);
        Bucket ipBucket   = ipBuckets.computeIfAbsent(ip,         this::newIpBucket);

        if (!userBucket.tryConsume(1)) {
            log.warn("Lookup rate limit exceeded for userId={}", userId);
            throw new RateLimitException("LOOKUP_RATE_LIMITED",
                    "Too many lookup requests. Maximum " + userCapacity
                    + " per minute. Please slow down.");
        }

        if (!ipBucket.tryConsume(1)) {
            log.warn("Lookup IP rate limit exceeded for ip={}", maskIp(ip));
            throw new RateLimitException("LOOKUP_IP_RATE_LIMITED",
                    "Too many lookup requests from this network. Please slow down.");
        }
    }

    // ── Bucket factories ───────────────────────────────────────

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

    /** Mask IP for safe logging — never log full IPv4 last octet. */
    private static String maskIp(String ip) {
        if (ip == null) return "unknown";
        int last = ip.lastIndexOf('.');
        return last > 0 ? ip.substring(0, last) + ".***" : "***";
    }
}
