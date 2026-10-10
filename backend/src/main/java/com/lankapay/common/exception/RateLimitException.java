package com.lankapay.common.exception;

/**
 * Thrown when a rate limit is exceeded (per-user or per-IP).
 * Maps to HTTP 429 Too Many Requests.
 */
public class RateLimitException extends LankaPayException {

    public RateLimitException(String message) {
        super("RATE_LIMITED", message);
    }

    public RateLimitException(String errorCode, String message) {
        super(errorCode, message);
    }
}
