package com.lankapay.common.exception;

/**
 * Thrown when a caller attempts an action they are not permitted to perform
 * on a specific resource (object-level authorization failure).
 * Maps to HTTP 403 Forbidden.
 */
public class AuthorizationException extends LankaPayException {

    public AuthorizationException(String message) {
        super("FORBIDDEN", message);
    }

    public AuthorizationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
