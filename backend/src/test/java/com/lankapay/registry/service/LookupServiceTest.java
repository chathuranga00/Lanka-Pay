package com.lankapay.registry.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.domain.BankAccount;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.repository.BankAccountRepository;
import com.lankapay.bank.repository.BankInstitutionRepository;
import com.lankapay.common.exception.RateLimitException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.registry.domain.LookupAbuseCounter;
import com.lankapay.registry.domain.LookupResultType;
import com.lankapay.registry.domain.Registry;
import com.lankapay.registry.repository.LookupAbuseCounterRepository;
import com.lankapay.registry.repository.RegistryRepository;
import com.lankapay.registry.web.LookupResponse;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LookupService + LookupService.safeDisplayName.
 * All collaborators mocked — no Spring context, no DB.
 */
@ExtendWith(MockitoExtension.class)
class LookupServiceTest {

    @Mock RegistryRepository           registryRepository;
    @Mock BankAccountRepository        bankAccountRepository;
    @Mock BankInstitutionRepository    institutionRepository;
    @Mock LookupAbuseCounterRepository abuseCounterRepository;
    @Mock LookupRateLimiter            rateLimiter;
    @Mock AuditService                 auditService;

    LookupService service;

    static final String PHONE   = "+94771234567";
    static final Long   ACTOR   = 1L;
    static final String IP      = "10.0.0.1";

    @BeforeEach
    void setUp() {
        service = new LookupService(
                registryRepository, bankAccountRepository, institutionRepository,
                abuseCounterRepository, rateLimiter, auditService, 50);
    }

    // ── Helpers ────────────────────────────────────────────────

    User user(Long id, String fullName) {
        User u = new User(PHONE, fullName, "199012345678", "hash");
        ReflectionTestUtils.setField(u, "id", id);
        u.setStatus(UserStatus.ACTIVE);
        u.setRole(UserRole.CUSTOMER);
        return u;
    }

    Registry registry(User owner, boolean discoverable) {
        Registry r = new Registry(owner, owner.getPhone());
        ReflectionTestUtils.setField(r, "id", 10L);
        r.setDiscoverable(discoverable);
        return r;
    }

    BankAccount bankAccount(Long id, Long userId, boolean receivingEnabled,
                            boolean discoverable, boolean isDefault) {
        User owner = user(userId, "Kamal Perera");
        BankAccount a = new BankAccount(owner, "SIMULATED_BANK_A",
                "hash", "****1001", "Kamal Perera", "enc");
        ReflectionTestUtils.setField(a, "id", id);
        a.setStatus(BankAccountStatus.ACTIVE);
        a.setReceivingEnabled(receivingEnabled);
        a.setDiscoverable(discoverable);
        a.setDefault(isDefault);
        return a;
    }

    LookupAbuseCounter freshCounter() {
        LookupAbuseCounter c = new LookupAbuseCounter(ACTOR,
                java.time.LocalDate.now(java.time.ZoneOffset.UTC));
        ReflectionTestUtils.setField(c, "id", 1L);
        return c;
    }

    void stubAbuseCounter() {
        LookupAbuseCounter counter = freshCounter();
        when(abuseCounterRepository.findByActorIdAndCounterDate(eq(ACTOR), any()))
                .thenReturn(Optional.of(counter));
        when(abuseCounterRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    // ── FOUND ──────────────────────────────────────────────────

    @Nested @DisplayName("FOUND")
    class Found {

        @Test
        @DisplayName("returns FOUND with masked accounts and abbreviated display name")
        void success() {
            User payee = user(2L, "Kamal Perera");
            Registry reg = registry(payee, true);
            BankAccount acct = bankAccount(5L, 2L, true, true, true);

            stubAbuseCounter();
            when(registryRepository.findByUserId_Phone(PHONE)).thenReturn(Optional.of(reg));
            when(bankAccountRepository.findByUserIdAndStatus(2L, BankAccountStatus.ACTIVE))
                    .thenReturn(List.of(acct));
            when(institutionRepository.findByBankCodeAndActiveTrue("SIMULATED_BANK_A"))
                    .thenReturn(Optional.empty());

            LookupResponse resp = service.lookup(PHONE, ACTOR, IP);

            assertThat(resp.resultType()).isEqualTo(LookupResultType.FOUND);
            assertThat(resp.displayName()).isEqualTo("Kamal P.");
            assertThat(resp.verificationStatus()).isEqualTo("VERIFIED");
            assertThat(resp.accounts()).hasSize(1);
            assertThat(resp.accounts().get(0).maskedNumber()).isEqualTo("****1001");
            // full account number must not appear
            assertThat(resp.accounts().get(0).maskedNumber()).doesNotContain("101001");

            verify(auditService).log(eq(ACTOR), eq("IDENTITY_LOOKUP"),
                    eq("REGISTRY"), eq(10L), contains("FOUND"), eq(IP));
        }

        @Test
        @DisplayName("only discoverable accounts are included in result")
        void onlyDiscoverableAccounts() {
            User payee = user(2L, "Kamal Perera");
            Registry reg = registry(payee, true);
            BankAccount discoverable = bankAccount(5L, 2L, true, true, true);
            BankAccount hidden       = bankAccount(6L, 2L, true, false, false);

            stubAbuseCounter();
            when(registryRepository.findByUserId_Phone(PHONE)).thenReturn(Optional.of(reg));
            when(bankAccountRepository.findByUserIdAndStatus(2L, BankAccountStatus.ACTIVE))
                    .thenReturn(List.of(discoverable, hidden));
            when(institutionRepository.findByBankCodeAndActiveTrue(any())).thenReturn(Optional.empty());

            LookupResponse resp = service.lookup(PHONE, ACTOR, IP);

            assertThat(resp.resultType()).isEqualTo(LookupResultType.FOUND);
            assertThat(resp.accounts()).hasSize(1);
        }
    }

    // ── NOT_FOUND ──────────────────────────────────────────────

    @Nested @DisplayName("NOT_FOUND")
    class NotFound {

        @Test
        @DisplayName("returns NOT_FOUND when phone has no registry entry")
        void noRegistryEntry() {
            stubAbuseCounter();
            when(registryRepository.findByUserId_Phone(PHONE)).thenReturn(Optional.empty());

            LookupResponse resp = service.lookup(PHONE, ACTOR, IP);

            assertThat(resp.resultType()).isEqualTo(LookupResultType.NOT_FOUND);
            assertThat(resp.displayName()).isNull();
            assertThat(resp.accounts()).isNull();
        }

        @Test
        @DisplayName("invalid phone format → ValidationException from normalizer")
        void invalidPhoneFormat() {
            // rate limiter is called first — let it pass
            // the normalizer then throws ValidationException
            assertThatThrownBy(() -> service.lookup("not-a-phone", ACTOR, IP))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo("INVALID_PHONE_FORMAT");
        }
    }

    // ── DISCOVERY_DISABLED ─────────────────────────────────────

    @Nested @DisplayName("DISCOVERY_DISABLED")
    class DiscoveryDisabled {

        @Test
        @DisplayName("returns DISCOVERY_DISABLED when user opted out")
        void discoveryOff() {
            User payee = user(2L, "Kamal Perera");
            Registry reg = registry(payee, false); // discoverable = false

            stubAbuseCounter();
            when(registryRepository.findByUserId_Phone(PHONE)).thenReturn(Optional.of(reg));

            LookupResponse resp = service.lookup(PHONE, ACTOR, IP);

            assertThat(resp.resultType()).isEqualTo(LookupResultType.DISCOVERY_DISABLED);
            assertThat(resp.displayName()).isNull();
            // Never returns bank account info
            assertThat(resp.accounts()).isNull();
        }
    }

    // ── NO_ELIGIBLE_ACCOUNT ────────────────────────────────────

    @Nested @DisplayName("NO_ELIGIBLE_ACCOUNT")
    class NoEligibleAccount {

        @Test
        @DisplayName("returns NO_ELIGIBLE_ACCOUNT when all accounts have receivingEnabled=false")
        void allReceivingDisabled() {
            User payee = user(2L, "Nimali Silva");
            Registry reg = registry(payee, true);
            BankAccount blocked = bankAccount(5L, 2L, false, true, true);

            stubAbuseCounter();
            when(registryRepository.findByUserId_Phone(PHONE)).thenReturn(Optional.of(reg));
            when(bankAccountRepository.findByUserIdAndStatus(2L, BankAccountStatus.ACTIVE))
                    .thenReturn(List.of(blocked));

            LookupResponse resp = service.lookup(PHONE, ACTOR, IP);

            assertThat(resp.resultType()).isEqualTo(LookupResultType.NO_ELIGIBLE_ACCOUNT);
        }

        @Test
        @DisplayName("returns NO_ELIGIBLE_ACCOUNT when account list is empty")
        void noAccounts() {
            User payee = user(2L, "Nimali Silva");
            Registry reg = registry(payee, true);

            stubAbuseCounter();
            when(registryRepository.findByUserId_Phone(PHONE)).thenReturn(Optional.of(reg));
            when(bankAccountRepository.findByUserIdAndStatus(2L, BankAccountStatus.ACTIVE))
                    .thenReturn(List.of());

            LookupResponse resp = service.lookup(PHONE, ACTOR, IP);

            assertThat(resp.resultType()).isEqualTo(LookupResultType.NO_ELIGIBLE_ACCOUNT);
        }
    }

    // ── PRIVACY_RESTRICTED ─────────────────────────────────────

    @Nested @DisplayName("PRIVACY_RESTRICTED")
    class PrivacyRestricted {

        @Test
        @DisplayName("returns PRIVACY_RESTRICTED when all accounts have account-level discoverable=false")
        void allAccountsHidden() {
            User payee = user(2L, "Saman Fernando");
            Registry reg = registry(payee, true);
            // receivingEnabled=true but discoverable=false at account level
            BankAccount hidden = bankAccount(5L, 2L, true, false, true);

            stubAbuseCounter();
            when(registryRepository.findByUserId_Phone(PHONE)).thenReturn(Optional.of(reg));
            when(bankAccountRepository.findByUserIdAndStatus(2L, BankAccountStatus.ACTIVE))
                    .thenReturn(List.of(hidden));

            LookupResponse resp = service.lookup(PHONE, ACTOR, IP);

            assertThat(resp.resultType()).isEqualTo(LookupResultType.PRIVACY_RESTRICTED);
            assertThat(resp.accounts()).isNull();
        }
    }

    // ── Rate limiting ──────────────────────────────────────────

    @Nested @DisplayName("Rate limiting")
    class RateLimiting {

        @Test
        @DisplayName("rate limit exceeded → RateLimitException before any DB query")
        void rateLimitBlocks() {
            doThrow(new RateLimitException("Too many requests"))
                    .when(rateLimiter).consume(ACTOR, IP);

            assertThatThrownBy(() -> service.lookup(PHONE, ACTOR, IP))
                    .isInstanceOf(RateLimitException.class)
                    .extracting("errorCode").isEqualTo("RATE_LIMITED");

            // No DB queries should have been made
            verifyNoInteractions(registryRepository);
            verifyNoInteractions(bankAccountRepository);
        }
    }

    // ── safeDisplayName ────────────────────────────────────────

    @Nested @DisplayName("safeDisplayName()")
    class SafeDisplayName {

        @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
        @CsvSource({
            "Kamal Perera,        Kamal P.",
            "Nimali Silva,        Nimali S.",
            "Saman,               Saman",
            "A B C,               A C.",
            "'',                  User",
        })
        void abbreviates(String input, String expected) {
            assertThat(LookupService.safeDisplayName(input.trim()))
                    .isEqualTo(expected.trim());
        }

        @Test
        @DisplayName("null input returns 'User'")
        void nullInput() {
            assertThat(LookupService.safeDisplayName(null)).isEqualTo("User");
        }
    }
}
