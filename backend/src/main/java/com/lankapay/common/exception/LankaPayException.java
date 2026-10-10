package com.lankapay.common.exception;

/**
 * Base exception for all Lanka Pay application errors.
 * Subclass this rather than throwing raw RuntimeExceptions.
 */
public class LankaPayException extends RuntimeException {

    private final String errorCode;

    public LankaPayException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public LankaPayException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
