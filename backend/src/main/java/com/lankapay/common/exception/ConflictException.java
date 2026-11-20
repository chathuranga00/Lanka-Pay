package com.lankapay.common.exception;

/**
 * Thrown when a request conflicts with existing state
 * (e.g. duplicate phone, already-used QR code).
 * Maps to HTTP 409 Conflict.
 */
public class ConflictException extends LankaPayException {

    public ConflictException(String errorCode, String message) {
        super(errorCode, message);
    }
}
