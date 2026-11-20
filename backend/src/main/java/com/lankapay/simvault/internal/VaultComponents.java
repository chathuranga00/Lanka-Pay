package com.lankapay.simvault.internal;

import com.lankapay.audit.service.AuditService;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.identity.otp.OtpProvider;

import java.util.List;

/**
 * Package-internal factory that constructs all vault components from the same package,
 * then exposes them via package-accessible accessors to {@code SimulatedCardVault}.
 *
 * This keeps all internals package-private while still letting
 * {@code SimulatedCardVault} (in the parent package) use them via this facade.
 */
public final class VaultComponents {

    private final VaultEncryptor           encryptor;
    private final VaultTokenStore          tokenStore;
    private final VaultRateLimiter         rateLimiter;
    private final VaultCardValidator       validator;
    private final VaultOtpChallengeService challengeService;

    public VaultComponents(
            String vaultKey,
            long   tokenTtlMinutes,
            long   uRate, long ipRate,
            int    maxFail, long lockout,
            OtpProvider otpProvider,
            UserStore   userStore,
            AuditService auditService) {

        this.encryptor        = new VaultEncryptor(vaultKey);
        this.tokenStore       = new VaultTokenStore(tokenTtlMinutes);
        this.rateLimiter      = new VaultRateLimiter(uRate, ipRate, maxFail, lockout);
        List<VaultCard> cards = VaultCardSeed.build(encryptor);
        this.validator        = new VaultCardValidator(cards);
        this.challengeService = new VaultOtpChallengeService(otpProvider, userStore, auditService);
    }

    public VaultEncryptor           encryptor()        { return encryptor; }
    public VaultTokenStore          tokenStore()       { return tokenStore; }
    public VaultRateLimiter         rateLimiter()      { return rateLimiter; }
    public VaultCardValidator       validator()        { return validator; }
    public VaultOtpChallengeService challengeService() { return challengeService; }
}
