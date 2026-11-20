package com.lankapay.simvault;

import com.lankapay.audit.service.AuditService;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.identity.otp.OtpProvider;
import com.lankapay.simvault.internal.SimulatedCardVaultImpl;
import com.lankapay.simvault.internal.VaultComponents;
import com.lankapay.virtualcard.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Public Spring bean that exposes the simulated card vault as a {@link CardProvider}.
 *
 * This class is intentionally a thin facade — all business logic lives in
 * {@link com.lankapay.simvault.internal.SimulatedCardVaultImpl} which is package-private.
 *
 * Active only when {@code app.mode=SIMULATED} (the default).
 *
 * ⚠ UNIVERSITY PROTOTYPE — no real card network, no real money.
 *   Every response carries {@code simulated=true} and brand label "TEST CARD".
 *
 * Isolation enforced by ArchUnit (SimVaultIsolationTest):
 *   - No module outside com.lankapay.simvault and com.lankapay.virtualcard.provider
 *     may import simvault internals.
 */
@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "SIMULATED", matchIfMissing = true)
public class SimulatedCardVault implements CardProvider {

    private final SimulatedCardVaultImpl impl;

    @Autowired
    public SimulatedCardVault(
            @Value("${app.encryption.vault-card-key}")                              String vaultKey,
            @Value("${app.sim-vault.token-ttl-minutes:15}")                         long   tokenTtl,
            @Value("${app.sim-vault.tokenize-rate-limit-per-user-per-minute:3}")    long   uRate,
            @Value("${app.sim-vault.tokenize-rate-limit-per-ip-per-minute:5}")      long   ipRate,
            @Value("${app.sim-vault.tokenize-max-failures:5}")                      int    maxFail,
            @Value("${app.sim-vault.tokenize-lockout-minutes:15}")                  long   lockout,
            OtpProvider  otpProvider,
            UserStore    userStore,
            AuditService auditService) {

        VaultComponents vc = new VaultComponents(
                vaultKey, tokenTtl, uRate, ipRate, maxFail, lockout,
                otpProvider, userStore, auditService);
        this.impl = new SimulatedCardVaultImpl(vc);
    }

    /** Package-visible constructor for unit tests (bypasses Spring). */
    SimulatedCardVault(SimulatedCardVaultImpl impl) {
        this.impl = impl;
    }

    @Override public String getProviderCode()  { return "SIMULATED"; }

    @Override public TokenizeResponse   tokenize(TokenizeRequest r)               { return impl.tokenize(r); }
    @Override public CardTokenInfo      getTokenInfo(String t, Long u)             { return impl.getTokenInfo(t, u); }
    @Override public String             startOwnershipChallenge(String t, Long u, String ip) { return impl.startOwnershipChallenge(t, u, ip); }
    @Override public CardProviderResponse confirmChallenge(String r, Long u, String o)       { return impl.confirmChallenge(r, u, o); }
    @Override public CardAuthResponse   authorize(CardAuthRequest r)               { return impl.authorize(r); }
    @Override public CardAuthResponse   capture(String ac, String opRef)           { return impl.capture(ac, opRef); }
    @Override public CardAuthResponse   voidAuth(String ac, String opRef)          { return impl.voidAuth(ac, opRef); }
    @Override public CardAuthResponse   refund(CardAuthRequest r)                  { return impl.refund(r); }
    @Override public CardAuthResponse   queryPayment(String opRef, String t)       { return impl.queryPayment(opRef, t); }
    @Override public CardProviderResponse setStatus(CardStatusRequest r)           { return impl.setStatus(r); }
    @Override public CardTokenInfo      revealDetails(String t, Long u, String cr) { return impl.revealDetails(t, u, cr); }
    @Override public CardTokenInfo      issueVirtualCard(Long u)                   { return impl.issueVirtualCard(u); }
    @Override public CardRevealDetails  revealVirtualCard(String t, Long u)        { return impl.revealVirtualCard(t, u); }
}
