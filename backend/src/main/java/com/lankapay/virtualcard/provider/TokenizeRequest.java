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
        String operationReference,
        String clientIp
) {
    public TokenizeRequest(String pan, String expMonth, String expYear, String cvv,
                           String holderName, Long requestingUserId, String operationReference) {
        this(pan, expMonth, expYear, cvv, holderName, requestingUserId, operationReference, "127.0.0.1");
    }

    @Override
    public String toString() {
        String last4 = (pan != null && pan.length() >= 4) ? pan.substring(pan.length() - 4) : "****";
        return "TokenizeRequest[pan=****" + last4
                + ", expMonth=**, expYear=**, cvv=***, holderName=" + holderName
                + ", requestingUserId=" + requestingUserId
                + ", operationReference=" + operationReference
                + ", clientIp=" + clientIp + "]";
    }
}
