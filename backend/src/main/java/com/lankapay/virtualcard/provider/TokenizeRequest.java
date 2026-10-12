package com.lankapay.virtualcard.provider;

/**
 * Carries raw card data from the controller to the vault.
 *
 * SECURITY: This record must NEVER be serialised to JSON, written to any log,
 * included in a stack trace, or stored anywhere. Fields are zeroed by the
 * vault implementation immediately after use.
 *
 * It is intentionally not a Jakarta-validated record — validation is done
 * on the HTTP-layer DTO before constructing this object.
 */
public record TokenizeRequest(
        String pan,          // 16-digit — zeroed after tokenize
        String expMonth,     // 2-digit MM
        String expYear,      // 2-digit YY
        String cvv,          // 3-4 digits — discarded, never stored
        String holderName,
        Long   requestingUserId,
        String operationReference
) {}
