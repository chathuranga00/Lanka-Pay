package com.lankapay.common.exception;

/**
 * Thrown when a requested resource does not exist (or is not visible to the caller).
 * Maps to HTTP 404 Not Found.
 */
public class ResourceNotFoundException extends LankaPayException {

    public ResourceNotFoundException(String resourceType, Object id) {
        super("RESOURCE_NOT_FOUND",
              "%s not found: %s".formatted(resourceType, id));
    }

    public ResourceNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }
}
