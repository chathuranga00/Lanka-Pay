package com.lankapay.common.web;

import com.lankapay.common.exception.AccountLockedException;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.InvalidCredentialsException;
import com.lankapay.common.exception.OtpException;
import com.lankapay.common.exception.RateLimitException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.TokenExpiredException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.common.filter.CorrelationIdFilter;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * Centralised exception → HTTP response mapping.
 * Stack traces are NEVER written to the response body.
 * PII is NEVER logged.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Domain exceptions ──────────────────────────────────────

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        log.debug("Validation error [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, ex.getErrorCode(), ex.getMessage(), correlationId()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        log.debug("Resource not found [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, ex.getErrorCode(), ex.getMessage(), correlationId()));
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ErrorResponse> handleAuthorization(AuthorizationException ex) {
        log.warn("Authorization failure [{}]", ex.getErrorCode());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, ex.getErrorCode(), ex.getMessage(), correlationId()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        log.debug("Conflict [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, ex.getErrorCode(), ex.getMessage(), correlationId()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        log.debug("Invalid credentials attempt");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, ex.getErrorCode(), ex.getMessage(), correlationId()));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(AccountLockedException ex) {
        log.warn("Account locked out until {}", ex.getLockedUntil());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.of(429, ex.getErrorCode(), ex.getMessage(), correlationId()));
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitException ex) {
        log.warn("Rate limit exceeded [{}]", ex.getErrorCode());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.of(429, ex.getErrorCode(), ex.getMessage(), correlationId()));
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpired(TokenExpiredException ex) {
        log.debug("Token expired or revoked");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, ex.getErrorCode(), ex.getMessage(), correlationId()));
    }

    @ExceptionHandler(OtpException.class)
    public ResponseEntity<ErrorResponse> handleOtp(OtpException ex) {
        log.debug("OTP error [{}]", ex.getErrorCode());
        int status = switch (ex.getErrorCode()) {
            case "OTP_EXPIRED"       -> 410;
            case "OTP_MAX_ATTEMPTS"  -> 429;
            default                  -> 400;
        };
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status, ex.getErrorCode(), ex.getMessage(), correlationId()));
    }

    // ── Spring Security exceptions ─────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "FORBIDDEN", "Access denied", correlationId()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        log.debug("Authentication failure: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, "UNAUTHORIZED", "Authentication required", correlationId()));
    }

    // ── Bean Validation (@Valid) ───────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {

        List<Map<String, String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldError)
                .toList();

        log.debug("Request validation failed: {} field error(s)", fieldErrors.size());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "VALIDATION_ERROR",
                        "Request validation failed", correlationId(), fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        log.debug("Constraint violation: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "VALIDATION_ERROR",
                        "Constraint violation", correlationId()));
    }

    // ── Catch-all ──────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        // Log at ERROR but never expose internal detail in the response
        log.error("Unexpected error [correlationId={}]", correlationId(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "INTERNAL_ERROR",
                        "An unexpected error occurred", correlationId()));
    }

    // ── Helpers ────────────────────────────────────────────────

    private String correlationId() {
        String id = MDC.get(CorrelationIdFilter.MDC_KEY);
        return id != null ? id : "unknown";
    }

    private Map<String, String> toFieldError(FieldError fe) {
        return Map.of(
                "field", fe.getField(),
                "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value"
        );
    }
}
