package com.lankapay.registry.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.domain.BankAccount;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.repository.BankAccountRepository;
import com.lankapay.bank.repository.BankInstitutionRepository;
import com.lankapay.common.util.PhoneNumberNormalizer;
import com.lankapay.registry.domain.LookupAbuseCounter;
import com.lankapay.registry.domain.LookupResultType;
import com.lankapay.registry.domain.Registry;
import com.lankapay.registry.repository.LookupAbuseCounterRepository;
import com.lankapay.registry.repository.RegistryRepository;
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
 * - Rate limiting is enforced BEFORE any DB query (via {@link LookupRateLimiter}).
 * - All non-FOUND outcomes return the same HTTP 404 to the caller;
 *   the resultType field differentiates UX states only for authenticated callers.
 * - The display name is abbreviated (first name + last initial) — never full name.
 * - Phone numbers are NEVER included in the response.
 * - NIC numbers are NEVER included in the response.
 * - Full account numbers are NEVER included (masked number only).
 * - Per-user daily abuse counters are incremented and flagged at threshold.
 *
 * Lookup states (maps to {@link LookupResultType}):
 *   FOUND              – phone registered, discoverable, has eligible accounts.
 *   NOT_FOUND          – phone not in registry, or registry but never verified.
 *   DISCOVERY_DISABLED – user explicitly opted out of discovery.
 *   NO_ELIGIBLE_ACCOUNT – discoverable but all accounts are removed/receiving-disabled.
 *   PRIVACY_RESTRICTED  – all accounts have account-level discoverable=false.
 */
@Service
public class LookupService {

    private static final Logger log = LoggerFactory.getLogger(LookupService.class);

    private final RegistryRepository         registryRepository;
    private final BankAccountRepository      bankAccountRepository;
    private final BankInstitutionRepository  institutionRepository;
    private final LookupAbuseCounterRepository abuseCounterRepository;
    private final LookupRateLimiter          rateLimiter;
    private final AuditService               auditService;
    private final int                        abuseDailyThreshold;

    public LookupService(
            RegistryRepository registryRepository,
            BankAccountRepository bankAccountRepository,
            BankInstitutionRepository institutionRepository,
            LookupAbuseCounterRepository abuseCounterRepository,
            LookupRateLimiter rateLimiter,
            AuditService auditService,
            @Value("${app.lookup.abuse-daily-threshold:50}") int abuseDailyThreshold) {

        this.registryRepository      = registryRepository;
        this.bankAccountRepository   = bankAccountRepository;
        this.institutionRepository   = institutionRepository;
        this.abuseCounterRepository  = abuseCounterRepository;
        this.rateLimiter             = rateLimiter;
        this.auditService            = auditService;
        this.abuseDailyThreshold     = abuseDailyThreshold;
    }

    /**
     * Look up a payment identity by phone number.
     *
     * @param rawPhone   Phone in any recognised SL format
     * @param actorId    Authenticated caller's user ID
     * @param ipAddress  Caller IP (for rate-limiting + audit)
     * @return           LookupResponse with typed result state
     */
    @Transactional
    public LookupResponse lookup(String rawPhone, Long actorId, String ipAddress) {

        // ── 1. Rate-limit check (before any DB work) ───────────
        rateLimiter.consume(actorId, ipAddress);

        // ── 2. Normalise phone ─────────────────────────────────
        String e164 = PhoneNumberNormalizer.normalize(rawPhone);

        // ── 3. Abuse counter ───────────────────────────────────
        incrementAbuseCounter(actorId);

        // ── 4. Registry lookup ─────────────────────────────────
        // First check without the discoverable filter to classify the result type
        Optional<Registry> registryOpt = registryRepository.findByUserId_Phone(e164);

        if (registryOpt.isEmpty()) {
            auditLookup(actorId, null, LookupResultType.NOT_FOUND, ipAddress);
            log.debug("Lookup NOT_FOUND actorId={}", actorId);
            return LookupResponse.notFound();
        }

        Registry registry = registryOpt.get();

        // ── 5. Discovery flag check ────────────────────────────
        if (!registry.isDiscoverable()) {
            auditLookup(actorId, registry.getId(), LookupResultType.DISCOVERY_DISABLED, ipAddress);
            log.debug("Lookup DISCOVERY_DISABLED actorId={}", actorId);
            return LookupResponse.discoveryDisabled();
        }

        // ── 6. Fetch active accounts ───────────────────────────
        Long payeeId = registry.getUser().getId();
        List<BankAccount> active = bankAccountRepository
                .findByUserIdAndStatus(payeeId, BankAccountStatus.ACTIVE);

        // Filter: receivingEnabled=true AND account-level discoverable=true
        List<BankAccount> eligible = active.stream()
                .filter(BankAccount::canReceive)
                .filter(BankAccount::isDiscoverable)
                .toList();

        List<BankAccount> receivable = active.stream()
                .filter(BankAccount::canReceive)
                .toList();

        if (receivable.isEmpty()) {
            auditLookup(actorId, registry.getId(), LookupResultType.NO_ELIGIBLE_ACCOUNT, ipAddress);
            return LookupResponse.noEligibleAccount();
        }

        if (eligible.isEmpty()) {
            // Has receivable accounts but all have account-level discoverable=false
            auditLookup(actorId, registry.getId(), LookupResultType.PRIVACY_RESTRICTED, ipAddress);
            return LookupResponse.privacyRestricted();
        }

        // ── 7. Build masked account summaries ──────────────────
        List<EligibleAccountSummary> summaries = eligible.stream()
                .map(a -> new EligibleAccountSummary(
                        a.getBankCode(),
                        bankDisplayName(a.getBankCode()),
                        a.getMaskedNumber(),
                        a.isDefault()))
                .toList();

        // ── 8. Safe display name (first name + last initial) ───
        String displayName = safeDisplayName(registry.getUser().getFullName());

        auditLookup(actorId, registry.getId(), LookupResultType.FOUND, ipAddress);
        log.debug("Lookup FOUND actorId={} accounts={}", actorId, summaries.size());
        return LookupResponse.found(displayName, summaries);
    }

    // ── Helpers ────────────────────────────────────────────────

    /**
     * Increment the caller's daily abuse counter and flag if threshold reached.
     * Uses REQUIRES_NEW propagation inside AuditService; here we write directly.
     */
    private void incrementAbuseCounter(Long actorId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LookupAbuseCounter counter = abuseCounterRepository
                .findByActorIdAndCounterDate(actorId, today)
                .orElseGet(() -> abuseCounterRepository.save(
                        new LookupAbuseCounter(actorId, today)));

        int count = counter.increment();
        if (count >= abuseDailyThreshold && !counter.isFlagged()) {
            counter.flag();
            log.warn("Lookup abuse threshold reached actorId={} count={}", actorId, count);
            auditService.log(actorId, "LOOKUP_ABUSE_FLAGGED", "USER",
                    actorId, "{\"dailyCount\":" + count + "}", null);
        }
        abuseCounterRepository.save(counter);
    }

    private void auditLookup(Long actorId, Long registryId,
                              LookupResultType result, String ip) {
        auditService.log(actorId, "IDENTITY_LOOKUP", "REGISTRY",
                registryId,
                "{\"result\":\"" + result.name() + "\"}",
                ip);
    }

    /**
     * Returns "First L." — first name plus last-name initial.
     * Never returns the full name.
     */
    static String safeDisplayName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "User";
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0];
        if (parts.length == 1) return first;
        String lastInitial = parts[parts.length - 1].substring(0, 1).toUpperCase();
        return first + " " + lastInitial + ".";
    }

    private String bankDisplayName(String bankCode) {
        return institutionRepository.findByBankCodeAndActiveTrue(bankCode)
                .map(i -> i.getDisplayName())
                .orElse(bankCode);
    }
}
