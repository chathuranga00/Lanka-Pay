package com.lankapay.simvault.internal;

import com.lankapay.common.exception.RateLimitException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-user and per-IP rate limiter + failure lockout for the tokenize endpoint.
 *
 * ISOLATION: package-private — never exported outside com.lankapay.simvault.
 *
 * Two layers:
 *   1. Bucket4j token bucket per user (requests / minute)
 *   2. Bucket4j token bucket per IP  (requests / minute)
 *   3. Failure counter per user: after maxFailures consecutive failures
 *      the user is locked for lockoutMinutes (mirroring login lockout).
 */
final class VaultRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(VaultRateLimiter.class);

    private final long userCapacity;
    private final long ipCapacity;
    private final int  maxFailures;
    private final long lockoutMinutes;

    private final ConcurrentHashMap<Long,   Bucket>         userBuckets  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket>         ipBuckets    = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long,   AtomicInteger>  failureCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long,   Instant>        lockedUntil   = new ConcurrentHashMap<>();

    VaultRateLimiter(long userCapacity, long ipCapacity, int maxFailures, long lockoutMinutes) {
        this.userCapacity  = userCapacity;
        this.ipCapacity    = ipCapacity;
        this.maxFailures   = maxFailures;
        this.lockoutMinutes = lockoutMinutes;
    }

    /** Consume one token. Throws on rate-limit or lockout. */
    void consume(Long userId, String ip) {
        // Lockout check first (no bucket consumed while locked)
        Instant locked = lockedUntil.get(userId);
        if (locked != null && Instant.now().isBefore(locked)) {
            long seconds = Duration.between(Instant.now(), locked).getSeconds();
            throw new RateLimitException("VAULT_TOKENIZE_LOCKED",
                    "Too many failed attempts. Try again in " + seconds + " seconds.");
        }

        Bucket ub = userBuckets.computeIfAbsent(userId, id ->
                Bucket.builder().addLimit(Bandwidth.builder()
                        .capacity(userCapacity)
                        .refillGreedy(userCapacity, Duration.ofMinutes(1))
                        .build()).build());

        Bucket ib = ipBuckets.computeIfAbsent(ip, i ->
                Bucket.builder().addLimit(Bandwidth.builder()
                        .capacity(ipCapacity)
                        .refillGreedy(ipCapacity, Duration.ofMinutes(1))
                        .build()).build());

        if (!ub.tryConsume(1)) {
            log.warn("[VAULT] Rate limit exceeded userId={}", userId);
            throw new RateLimitException("VAULT_TOKENIZE_RATE_LIMITED",
                    "Too many tokenize requests. Please slow down.");
        }
        if (!ib.tryConsume(1)) {
            log.warn("[VAULT] IP rate limit exceeded ip={}", maskIp(ip));
            throw new RateLimitException("VAULT_TOKENIZE_IP_RATE_LIMITED",
                    "Too many requests from this network.");
        }
    }

    /** Call on a validation failure. Locks the user after maxFailures. */
    void recordFailure(Long userId) {
        AtomicInteger counter = failureCounts.computeIfAbsent(userId, id -> new AtomicInteger(0));
        int count = counter.incrementAndGet();
        if (count >= maxFailures) {
            lockedUntil.put(userId, Instant.now().plus(Duration.ofMinutes(lockoutMinutes)));
            counter.set(0);
            log.warn("[VAULT] userId={} locked after {} failures", userId, count);
        }
    }

    /** Call on success to reset the failure counter. */
    void recordSuccess(Long userId) {
        failureCounts.remove(userId);
        lockedUntil.remove(userId);
    }

    /** Visible for testing. */
    boolean isLockedOut(Long userId) {
        Instant locked = lockedUntil.get(userId);
        return locked != null && Instant.now().isBefore(locked);
    }

    private static String maskIp(String ip) {
        if (ip == null) return "unknown";
        int last = ip.lastIndexOf('.');
        return last > 0 ? ip.substring(0, last) + ".***" : "***";
    }
}
