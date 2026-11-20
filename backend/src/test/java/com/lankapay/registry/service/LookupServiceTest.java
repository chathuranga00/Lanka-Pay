package com.lankapay.registry.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.RateLimitException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.registry.domain.*;
import com.lankapay.registry.web.LookupResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    @Mock PhoneIdentityStore phoneIdentityStore;
    @Mock LookupRateLimiter  rateLimiter;
    @Mock AuditService       auditService;

    LookupService service;

    static final String PHONE   = "+94771234567";
    static final Long   ACTOR   = 1L;
    static final String IP      = "10.0.0.1";

    @BeforeEach
    void setUp() {
        service = new LookupService(
                phoneIdentityStore, rateLimiter, auditService, 50);
    }

    // ── Helpers ────────────────────────────────────────────────

    PhoneIdentity identity(Long id, Long userId, String fullName, boolean discoverable) {
        return new PhoneIdentity(id, userId, PHONE, discoverable, fullName, Instant.now());
    }

    RegistryAccountRecord account(Long accountId, String bankCode, String bankName,
                                  String maskedNumber, boolean discoverable,
                                  boolean receivingEnabled, boolean isDefault) {
        return new RegistryAccountRecord(
                accountId, bankCode, bankName, maskedNumber,
                discoverable, receivingEnabled, isDefault, "ACTIVE"
        );
    }

    void stubAbuseCounter() {
        AbuseCounterRecord counter = new AbuseCounterRecord(ACTOR, LocalDate.now(ZoneOffset.UTC));
        when(phoneIdentityStore.findAbuseCounter(eq(ACTOR), any()))
                .thenReturn(Optional.of(counter));
        when(phoneIdentityStore.saveAbuseCounter(any())).thenAnswer(i -> i.getArgument(0));
    }

    // ── FOUND ──────────────────────────────────────────────────

    @Nested @DisplayName("FOUND")
    class Found {

        @Test
        @DisplayName("returns FOUND with masked accounts and abbreviated display name")
        void success() {
            PhoneIdentity id = identity(10L, 2L, "Kamal Perera", true);
            RegistryAccountRecord acct = account(5L, "SIMULATED_BANK_A", "Bank A", "****1001", true, true, true);

            stubAbuseCounter();
            when(phoneIdentityStore.findByPhone(PHONE)).thenReturn(Optional.of(id));
            when(phoneIdentityStore.findAccountsInRegistry(PHONE)).thenReturn(List.of(acct));

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
            PhoneIdentity id = identity(10L, 2L, "Kamal Perera", true);
            RegistryAccountRecord discoverable = account(5L, "SIMULATED_BANK_A", "Bank A", "****1001", true, true, true);
            RegistryAccountRecord hidden       = account(6L, "SIMULATED_BANK_B", "Bank B", "****1002", false, true, false);

            stubAbuseCounter();
            when(phoneIdentityStore.findByPhone(PHONE)).thenReturn(Optional.of(id));
            when(phoneIdentityStore.findAccountsInRegistry(PHONE)).thenReturn(List.of(discoverable, hidden));

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
            when(phoneIdentityStore.findByPhone(PHONE)).thenReturn(Optional.empty());

            LookupResponse resp = service.lookup(PHONE, ACTOR, IP);

            assertThat(resp.resultType()).isEqualTo(LookupResultType.NOT_FOUND);
            assertThat(resp.displayName()).isNull();
            assertThat(resp.accounts()).isNull();
        }

        @Test
        @DisplayName("invalid phone format → ValidationException from normalizer")
        void invalidPhoneFormat() {
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
            PhoneIdentity id = identity(10L, 2L, "Kamal Perera", false);

            stubAbuseCounter();
            when(phoneIdentityStore.findByPhone(PHONE)).thenReturn(Optional.of(id));

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
            PhoneIdentity id = identity(10L, 2L, "Nimali Silva", true);
            RegistryAccountRecord blocked = account(5L, "SIMULATED_BANK_A", "Bank A", "****1001", true, false, true);

            stubAbuseCounter();
            when(phoneIdentityStore.findByPhone(PHONE)).thenReturn(Optional.of(id));
            when(phoneIdentityStore.findAccountsInRegistry(PHONE)).thenReturn(List.of(blocked));

            LookupResponse resp = service.lookup(PHONE, ACTOR, IP);

            assertThat(resp.resultType()).isEqualTo(LookupResultType.NO_ELIGIBLE_ACCOUNT);
        }

        @Test
        @DisplayName("returns NO_ELIGIBLE_ACCOUNT when account list is empty")
        void noAccounts() {
            PhoneIdentity id = identity(10L, 2L, "Nimali Silva", true);

            stubAbuseCounter();
            when(phoneIdentityStore.findByPhone(PHONE)).thenReturn(Optional.of(id));
            when(phoneIdentityStore.findAccountsInRegistry(PHONE)).thenReturn(List.of());

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
            PhoneIdentity id = identity(10L, 2L, "Saman Fernando", true);
            // receivingEnabled=true but discoverable=false at account level
            RegistryAccountRecord hidden = account(5L, "SIMULATED_BANK_A", "Bank A", "****1001", false, true, true);

            stubAbuseCounter();
            when(phoneIdentityStore.findByPhone(PHONE)).thenReturn(Optional.of(id));
            when(phoneIdentityStore.findAccountsInRegistry(PHONE)).thenReturn(List.of(hidden));

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
            verifyNoInteractions(phoneIdentityStore);
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
