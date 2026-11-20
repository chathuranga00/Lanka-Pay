package com.lankapay.common.exception;

/**
 * Thrown when login credentials are wrong (phone not found, wrong password/PIN).
 * Always uses the same generic message to prevent user enumeration.
 * Maps to HTTP 401.
 */
public class InvalidCredentialsException extends LankaPayException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Invalid phone number or password");
    }

    public InvalidCredentialsException(String errorCode, String message) {
        super(errorCode, message);
    }
}
