package com.lankapay.common.exception;

/**
 * Thrown when a refresh token has expired or been revoked.
 * Maps to HTTP 401.
 */
public class TokenExpiredException extends LankaPayException {

    public TokenExpiredException() {
        super("TOKEN_EXPIRED", "Refresh token has expired or been revoked");
    }

    public TokenExpiredException(String message) {
        super("TOKEN_EXPIRED", message);
    }
}
