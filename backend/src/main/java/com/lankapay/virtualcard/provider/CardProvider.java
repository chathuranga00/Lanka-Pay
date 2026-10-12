package com.lankapay.virtualcard.provider;

/**
 * Public contract for in-process card provider operations.
 *
 * Isolation rule (enforced by ArchUnit):
 *   Only {@code com.lankapay.virtualcard..} and {@code com.lankapay.simvault..}
 *   may implement this interface.
 *   No other module may import {@code com.lankapay.simvault..} internals directly;
 *   they MUST use only this interface and the types in this package.
 *
 * ⚠ UNIVERSITY PROTOTYPE — all implementations are SIMULATED.
 * "TEST CARD" label must appear on every simulated card. No real card networks.
 */
public interface CardProvider {

    /** Provider identifier, e.g. "SIMULATED". */
    String getProviderCode();

    /**
     * Tokenize a raw card number (PAN + CVV).
     * Returns an opaque token; PAN and CVV are discarded inside the vault.
     * The token is bound to {@code request.requestingUserId()} and expires
     * after a short TTL (default 15 minutes) unless used to link a card.
     *
     * SECURITY: This is the ONLY method that ever receives raw card data.
     * The PAN in the request object must be zeroed by the implementation
     * immediately after processing.
     */
    TokenizeResponse tokenize(TokenizeRequest request);

    /**
     * Retrieve safe metadata about a token.
     * NEVER returns PAN, CVV, or any reconstructable card data.
     *
     * @throws com.lankapay.common.exception.ResourceNotFoundException if token unknown
     * @throws com.lankapay.common.exception.AuthorizationException   if token bound to different user
     */
    CardTokenInfo getTokenInfo(String cardToken, Long requestingUserId);

    /**
     * Start an ownership challenge by sending a TEST bank OTP to the user's phone.
     * Returns the challenge reference; client must confirm with {@link #confirmChallenge}.
     */
    String startOwnershipChallenge(String cardToken, Long userId, String ipAddress);

    /**
     * Confirm an ownership challenge with the OTP code.
     * Returns a confirmed challenge reference to be used in {@link #tokenize}.
     */
    CardProviderResponse confirmChallenge(String challengeRef, Long userId, String otp);

    /**
     * Authorize a debit.
     * NEVER return APPROVED until the vault confirms it.
     * Timeouts return {@link CardAuthStatus#TIMEOUT}; caller must reconcile.
     */
    CardAuthResponse authorize(CardAuthRequest request);

    /** Capture a pre-authorized amount (no-op in SIMULATED — auto-captured on authorize). */
    CardAuthResponse capture(String authCode, String operationReference);

    /** Void (cancel) a pre-authorized or captured amount. */
    CardAuthResponse voidAuth(String authCode, String operationReference);

    /** Refund a previously captured amount. */
    CardAuthResponse refund(CardAuthRequest request);

    /** Query status of a prior authorization. Used after timeout. */
    CardAuthResponse queryPayment(String operationReference, String cardToken);

    /** Update the vault's knowledge of the card status. */
    CardProviderResponse setStatus(CardStatusRequest request);

    /**
     * Reveal PAN and CVV for a card the vault issued.
     * Refuses (throws) for any token the vault did not issue.
     * Requires prior OTP confirmation (challengeRef must be valid).
     *
     * Returns a {@link CardTokenInfo} (safe metadata only) — PAN reveal is
     * handled separately inside the vault and is NOT part of this response
     * in the current prototype. Extend this when a UI reveal flow is needed.
     */
    CardTokenInfo revealDetails(String cardToken, Long userId, String challengeRef);
}
