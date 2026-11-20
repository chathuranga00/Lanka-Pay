package com.lankapay.common.exception;

/**
 * Thrown when a request fails domain-level validation.
 * Maps to HTTP 400 Bad Request.
 */
public class ValidationException extends LankaPayException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }

    public ValidationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
