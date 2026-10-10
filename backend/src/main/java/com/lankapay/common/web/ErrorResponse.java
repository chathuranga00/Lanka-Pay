package com.lankapay.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Standard error response body returned by GlobalExceptionHandler.
 * Stack traces are NEVER included.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String code,
        String message,
        String correlationId,
        Instant timestamp,
        Object errors          // optional: field-level validation errors
) {
    /** Convenience factory without field errors. */
    public static ErrorResponse of(int status, String code, String message, String correlationId) {
        return new ErrorResponse(status, code, message, correlationId, Instant.now(), null);
    }

    /** Convenience factory with field errors (e.g. @Valid failures). */
    public static ErrorResponse of(int status, String code, String message,
                                   String correlationId, Object errors) {
        return new ErrorResponse(status, code, message, correlationId, Instant.now(), errors);
    }
}
