package com.lankapay.registry.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.common.util.PhoneNumberNormalizer;
import com.lankapay.registry.domain.AbuseCounterRecord;
import com.lankapay.registry.domain.LookupResultType;
import com.lankapay.registry.domain.PhoneIdentity;
import com.lankapay.registry.domain.PhoneIdentityStore;
import com.lankapay.registry.domain.RegistryAccountRecord;
import com.lankapay.registry.web.EligibleAccountSummary;
import com.lankapay.registry.web.LookupResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Payment identity lookup service.
 *
 * Anti-enumeration design:
 * - Rate limiting is enforced BEFORE any query (via {@link LookupRateLimiter}).
 * - All non-FOUND outcomes return the same HTTP 404 to the caller;
 *   the resultType field differentiates UX states only for authenticated callers.
 * - The display name is abbreviated (first name + last initial) — never full name.
 * - Phone numbers are NEVER included in the response.
 * - NIC numbers are NEVER included in the response.
 * - Full account numbers are NEVER included (masked number only).
 * - Per-user daily abuse counters are incremented and flagged at threshold.
 */
@Service
public class LookupService {

    private static final Logger log = LoggerFactory.getLogger(LookupService.class);

    private final PhoneIdentityStore phoneIdentityStore;
    private final LookupRateLimiter  rateLimiter;
    private final AuditService       auditService;
    private final int                abuseDailyThreshold;

    public LookupService(
            PhoneIdentityStore phoneIdentityStore,
            LookupRateLimiter rateLimiter,
            AuditService auditService,
            @Value("${app.lookup.abuse-daily-threshold:50}") int abuseDailyThreshold) {

        this.phoneIdentityStore  = phoneIdentityStore;
        this.rateLimiter         = rateLimiter;
        this.auditService        = auditService;
        this.abuseDailyThreshold = abuseDailyThreshold;
    }

    /**
     * Look up a payment identity by phone number.
     */
    @Transactional
    public LookupResponse lookup(String rawPhone, Long actorId, String ipAddress) {

        // 1. Rate-limit check (before any query)
        rateLimiter.consume(actorId, ipAddress);

        // 2. Normalise phone
        String e164 = PhoneNumberNormalizer.normalize(rawPhone);

        // 3. Abuse counter
        incrementAbuseCounter(actorId);

        // 4. Registry lookup
        Optional<PhoneIdentity> identityOpt = phoneIdentityStore.findByPhone(e164);
        if (identityOpt.isEmpty()) {
            auditLookup(actorId, null, LookupResultType.NOT_FOUND, ipAddress);
            log.debug("Lookup NOT_FOUND actorId={}", actorId);
            return LookupResponse.notFound();
        }
        PhoneIdentity identity = identityOpt.get();
        if (!identity.discoverable()) {
            auditLookup(actorId, identity.id(), LookupResultType.DISCOVERY_DISABLED, ipAddress);
            log.debug("Lookup DISCOVERY_DISABLED actorId={}", actorId);
            return LookupResponse.discoveryDisabled();
        }
        Long registryId = identity.id();
        String fullName = identity.fullName();

        // 5. Fetch accounts from paymentRegistry
        List<RegistryAccountRecord> accounts = phoneIdentityStore.findAccountsInRegistry(e164);

        List<RegistryAccountRecord> receivable = accounts.stream()
                .filter(RegistryAccountRecord::canReceive)
                .toList();

        List<RegistryAccountRecord> eligible = accounts.stream()
                .filter(RegistryAccountRecord::isEligible)
                .toList();

        if (receivable.isEmpty()) {
            auditLookup(actorId, registryId, LookupResultType.NO_ELIGIBLE_ACCOUNT, ipAddress);
            return LookupResponse.noEligibleAccount();
        }

        if (eligible.isEmpty()) {
            auditLookup(actorId, registryId, LookupResultType.PRIVACY_RESTRICTED, ipAddress);
            return LookupResponse.privacyRestricted();
        }

        List<EligibleAccountSummary> summaries = eligible.stream()
                .map(a -> new EligibleAccountSummary(
                        a.bankCode(),
                        a.bankName() != null ? a.bankName() : a.bankCode(),
                        a.maskedNumber(),
                        a.isDefault()))
                .toList();

        // 6. Safe display name (first name + last initial)
        String displayName = safeDisplayName(fullName);

        auditLookup(actorId, registryId, LookupResultType.FOUND, ipAddress);
        log.debug("Lookup FOUND actorId={} accounts={}", actorId, summaries.size());
        return LookupResponse.found(displayName, summaries);
    }

    private void incrementAbuseCounter(Long actorId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        AbuseCounterRecord defaultCounter = new AbuseCounterRecord(actorId, today);
        AbuseCounterRecord counter = phoneIdentityStore
                .findAbuseCounter(actorId, today)
                .orElseGet(() -> {
                    AbuseCounterRecord saved = phoneIdentityStore.saveAbuseCounter(defaultCounter);
                    return saved != null ? saved : defaultCounter;
                });
        if (counter == null) {
            counter = defaultCounter;
        }

        AbuseCounterRecord incremented = counter.increment();
        if (incremented.lookupCount() >= abuseDailyThreshold && !incremented.flagged()) {
            incremented = incremented.flag();
            log.warn("Lookup abuse threshold reached actorId={} count={}", actorId, incremented.lookupCount());
            auditService.log(actorId, "LOOKUP_ABUSE_FLAGGED", "USER",
                    actorId, "{\"dailyCount\":" + incremented.lookupCount() + "}", null);
        }
        phoneIdentityStore.saveAbuseCounter(incremented);
    }

    private void auditLookup(Long actorId, Long registryId,
                              LookupResultType result, String ip) {
        auditService.log(actorId, "IDENTITY_LOOKUP", "REGISTRY",
                registryId,
                "{\"result\":\"" + result.name() + "\"}",
                ip);
    }

    static String safeDisplayName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "User";
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0];
        if (parts.length == 1) return first;
        String lastInitial = parts[parts.length - 1].substring(0, 1).toUpperCase();
        return first + " " + lastInitial + ".";
    }
}
