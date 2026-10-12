package com.lankapay.simvault.internal;

import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.virtualcard.provider.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Full implementation of the simulated card vault.
 * Package-private so only the public Spring facade
 * {@link com.lankapay.simvault.SimulatedCardVault} can instantiate it.
 *
 * ⚠ UNIVERSITY PROTOTYPE — no real card network, no real money.
 */
public final class SimulatedCardVaultImpl {

    private static final Logger log = LoggerFactory.getLogger(SimulatedCardVaultImpl.class);

    private final VaultComponents vc;
    private final ConcurrentHashMap<String, String> pendingAuths = new ConcurrentHashMap<>();

    public SimulatedCardVaultImpl(VaultComponents vc) {
        this.vc = vc;
    }

    // ── tokenize ──────────────────────────────────────────────

    public TokenizeResponse tokenize(TokenizeRequest request) {
        vc.rateLimiter().consume(request.requestingUserId(), "INTERNAL");
        try {
            VaultCard card = vc.validator().validate(
                    request.pan(), request.expMonth(), request.expYear(), request.cvv());
            String tk = UUID.randomUUID().toString();
            VaultToken vt = vc.tokenStore().issue(
                    tk, card.last4(), card.expMonth(), card.expYear(),
                    request.requestingUserId(), card.scenario());

            vc.rateLimiter().recordSuccess(request.requestingUserId());
            log.info("[SIMVAULT] tokenize userId={} last4={} scenario={}",
                    request.requestingUserId(), card.last4(), card.scenario());

            return new TokenizeResponse(tk, card.last4(), card.expMonth(), card.expYear(),
                    "TEST CARD", true, vt.expiresAt());

        } catch (com.lankapay.common.exception.ValidationException ex) {
            vc.rateLimiter().recordFailure(request.requestingUserId());
            log.warn("[SIMVAULT] tokenize rejected userId={}", request.requestingUserId());
            throw ex;
        }
    }

    // ── getTokenInfo ──────────────────────────────────────────

    public CardTokenInfo getTokenInfo(String cardToken, Long userId) {
        VaultToken vt = vc.tokenStore().requireValid(cardToken, userId);
        return toInfo(vt);
    }

    // ── ownership challenge ───────────────────────────────────

    public String startOwnershipChallenge(String cardToken, Long userId, String ip) {
        vc.tokenStore().requireValid(cardToken, userId);
        return vc.challengeService().startChallenge(userId, ip);
    }

    public CardProviderResponse confirmChallenge(String ref, Long userId, String otp) {
        String confirmed = vc.challengeService().confirmChallenge(ref, userId, otp);
        return CardProviderResponse.ok("Challenge confirmed: " + confirmed);
    }

    // ── authorize ─────────────────────────────────────────────

    public CardAuthResponse authorize(CardAuthRequest request) {
        VaultToken vt = vc.tokenStore().requireValid(request.cardToken(), request.cardOwnerUserId());
        VaultScenario vs = vt.scenario();

        log.info("[SIMVAULT] authorize last4={} amount={} scenario={}",
                vt.last4(), request.amount(), vs);

        return switch (vs) {
            case ALWAYS_APPROVED -> {
                String ac = "AUTH-" + uuid8();
                yield CardAuthResponse.approved(ac, vt.token(), vt.last4(), vs.cardScenario);
            }
            case INSUFFICIENT_FUNDS ->
                CardAuthResponse.declined("Insufficient funds", vt.token(), vt.last4(), vs.cardScenario);
            case CARD_EXPIRED ->
                CardAuthResponse.declined("Card expired", vt.token(), vt.last4(), vs.cardScenario);
            case REQUIRES_OTP_CHALLENGE ->
                CardAuthResponse.pendingChallenge(vt.token(), vt.last4());
            case PROVIDER_TIMEOUT -> {
                pendingAuths.put(request.operationReference(), vt.token());
                yield CardAuthResponse.timeout(vt.token(), vt.last4(), vs.cardScenario);
            }
            case DELAYED_CONFIRMATION -> {
                pendingAuths.put(request.operationReference(), vt.token());
                yield new CardAuthResponse(false, null, vt.token(), vt.last4(),
                        CardAuthStatus.PENDING_CHALLENGE, "Pending confirmation",
                        vs.cardScenario, true, Instant.now());
            }
            case HARD_DECLINE ->
                CardAuthResponse.declined("Card blocked", vt.token(), vt.last4(), vs.cardScenario);
            case LATER_REVERSAL -> {
                String ac = "AUTH-" + uuid8();
                pendingAuths.put(request.operationReference() + ":REVERSAL", vt.token());
                yield CardAuthResponse.approved(ac, vt.token(), vt.last4(), vs.cardScenario);
            }
        };
    }

    // ── capture / void / refund ───────────────────────────────

    public CardAuthResponse capture(String authCode, String operationReference) {
        return new CardAuthResponse(true, authCode, null, null,
                CardAuthStatus.APPROVED, null, CardScenario.ALWAYS_APPROVED, true, Instant.now());
    }

    public CardAuthResponse voidAuth(String authCode, String operationReference) {
        return new CardAuthResponse(true, authCode, null, null,
                CardAuthStatus.REVERSED, null, CardScenario.ALWAYS_APPROVED, true, Instant.now());
    }

    public CardAuthResponse refund(CardAuthRequest request) {
        return CardAuthResponse.approved("REF-" + uuid8(), request.cardToken(), "????",
                CardScenario.ALWAYS_APPROVED);
    }

    // ── queryPayment ──────────────────────────────────────────

    public CardAuthResponse queryPayment(String operationReference, String cardToken) {
        if (pendingAuths.remove(operationReference + ":REVERSAL") != null) {
            return CardAuthResponse.declined("Authorization reversed by issuer",
                    cardToken, "????", CardScenario.LATER_REVERSAL);
        }
        if (pendingAuths.remove(operationReference) != null) {
            return CardAuthResponse.approved("AUTH-" + uuid8(), cardToken, "????",
                    CardScenario.PROVIDER_TIMEOUT_BUT_CHARGED);
        }
        return new CardAuthResponse(true, null, cardToken, "????",
                CardAuthStatus.APPROVED, null, CardScenario.ALWAYS_APPROVED, true, Instant.now());
    }

    // ── setStatus ─────────────────────────────────────────────

    public CardProviderResponse setStatus(CardStatusRequest request) {
        vc.tokenStore().find(request.cardToken())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CARD_TOKEN_NOT_FOUND", "Card token not found"));
        return CardProviderResponse.ok("Status → " + request.targetStatus() + " [SIMULATED]");
    }

    // ── revealDetails ─────────────────────────────────────────

    public CardTokenInfo revealDetails(String cardToken, Long userId, String challengeRef) {
        VaultToken vt = vc.tokenStore().requireValid(cardToken, userId);
        log.info("[SIMVAULT] revealDetails userId={} last4={}", userId, vt.last4());
        return toInfo(vt);
    }

    // ── helpers ───────────────────────────────────────────────

    private CardTokenInfo toInfo(VaultToken vt) {
        return new CardTokenInfo(vt.token(), vt.last4(), vt.expMonth(), vt.expYear(),
                "TEST CARD", vt.boundToUserId(), vt.scenario().cardScenario,
                vt.isExpired(), true, vt.expiresAt(), vt.issuedAt());
    }

    private static String uuid8() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
