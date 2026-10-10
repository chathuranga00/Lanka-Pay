package com.lankapay.bank.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.adapter.AdapterResponse;
import com.lankapay.bank.adapter.BankAdapter;
import com.lankapay.bank.adapter.BankAdapterRegistry;
import com.lankapay.bank.crypto.AccountReferenceEncryptor;
import com.lankapay.bank.domain.*;
import com.lankapay.bank.repository.AccountConsentRepository;
import com.lankapay.bank.repository.BankAccountRepository;
import com.lankapay.bank.repository.BankInstitutionRepository;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BankAccountService.
 * All collaborators are mocked — no Spring context, no database.
 */
@ExtendWith(MockitoExtension.class)
class BankAccountServiceTest {

    @Mock BankAccountRepository     accountRepository;
    @Mock BankInstitutionRepository institutionRepository;
    @Mock AccountConsentRepository  consentRepository;
    @Mock UserRepository            userRepository;
    @Mock BankAdapterRegistry       adapterRegistry;
    @Mock BankAdapter               adapter;
    @Mock AuditService              auditService;

    AccountReferenceEncryptor encryptor;
    BankAccountService        service;

    static final Long   USER_ID    = 1L;
    static final Long   OTHER_ID   = 99L;
    static final String INST_CODE  = "SIMULATED_BANK_A";
    static final String ACCT_NUM   = "101001";
    static final String ACCT_NAME  = "Kamal Perera";
    static final String IP         = "127.0.0.1";

    @BeforeEach
    void setUp() {
        String testKey = Base64.getUrlEncoder().withoutPadding()
                               .encodeToString(new byte[32]);
        encryptor = new AccountReferenceEncryptor(testKey);

        service = new BankAccountService(
                accountRepository, institutionRepository, consentRepository,
                userRepository, adapterRegistry, encryptor, auditService);
    }

    // ── Helpers ────────────────────────────────────────────────

    User user(Long id) {
        User u = new User("+94771234567", "Kamal", "199012345678", "hash");
        ReflectionTestUtils.setField(u, "id", id);
        u.setStatus(UserStatus.ACTIVE);
        u.setRole(UserRole.CUSTOMER);
        return u;
    }

    BankInstitution institution() {
        // Use ReflectionTestUtils to bypass the protected constructor
        BankInstitution i = org.springframework.objenesis.ObjenesisHelper
                .newInstance(BankInstitution.class);
        ReflectionTestUtils.setField(i, "bankCode",   INST_CODE);
        ReflectionTestUtils.setField(i, "displayName","Simulated Bank A [SIMULATED]");
        ReflectionTestUtils.setField(i, "simulated",  true);
        ReflectionTestUtils.setField(i, "active",     true);
        return i;
    }

    BankAccount account(Long id, Long userId, BankAccountStatus status) {
        User owner = user(userId);
        BankAccount a = new BankAccount(owner, INST_CODE,
                BankAccountService.sha256(INST_CODE + ACCT_NUM),
                "****1001", ACCT_NAME, "enc-ref");
        ReflectionTestUtils.setField(a, "id", id);
        a.setStatus(status);
        return a;
    }

    // ── linkAccount ────────────────────────────────────────────

    @Nested @DisplayName("linkAccount()")
    class LinkAccount {

        @Test
        @DisplayName("success – persists account, consent, audit")
        void success() {
            User u = user(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(u));
            when(institutionRepository.findByBankCodeAndActiveTrue(INST_CODE))
                    .thenReturn(Optional.of(institution()));
            when(accountRepository.findByAccountNumberHash(any())).thenReturn(Optional.empty());
            when(adapterRegistry.get(INST_CODE)).thenReturn(adapter);
            when(adapter.verifyOwnership(any()))
                    .thenReturn(AdapterResponse.success("SIM-A-123", "OWNERSHIP_VERIFIED"));
            when(accountRepository.save(any())).thenAnswer(inv -> {
                BankAccount a = inv.getArgument(0);
                ReflectionTestUtils.setField(a, "id", 10L);
                return a;
            });
            when(consentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            BankAccount result = service.linkAccount(
                    USER_ID, INST_CODE, ACCT_NUM, ACCT_NAME, "My Account", true, IP);

            assertThat(result.getMaskedNumber()).isEqualTo("****1001");
            assertThat(result.isActive()).isTrue();
            assertThat(result.isReceivingEnabled()).isTrue();
            assertThat(result.getLabel()).isEqualTo("My Account");

            // encryptedAccountReference must be set
            assertThat(result.getEncryptedAccountReference()).isNotBlank();
            // but it must NOT equal the plaintext account number
            assertThat(result.getEncryptedAccountReference()).isNotEqualTo(ACCT_NUM);

            verify(consentRepository).save(argThat(c ->
                    c.getConsentType() == ConsentType.LINK_ACCOUNT
                    && c.isConsentGiven()));
            verify(auditService).log(eq(USER_ID), eq("BANK_ACCOUNT_LINKED"),
                    any(), any(), any(), eq(IP));
        }

        @Test
        @DisplayName("rejects when consentGiven=false")
        void noConsent() {
            assertThatThrownBy(() -> service.linkAccount(
                    USER_ID, INST_CODE, ACCT_NUM, ACCT_NAME, null, false, IP))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo("CONSENT_REQUIRED");

            verifyNoInteractions(adapterRegistry);
        }

        @Test
        @DisplayName("rejects when institution not found")
        void institutionNotFound() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
            when(institutionRepository.findByBankCodeAndActiveTrue(INST_CODE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.linkAccount(
                    USER_ID, INST_CODE, ACCT_NUM, ACCT_NAME, null, true, IP))
                    .isInstanceOf(com.lankapay.common.exception.ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("rejects duplicate account (same hash already linked)")
        void duplicateAccount() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
            when(institutionRepository.findByBankCodeAndActiveTrue(INST_CODE))
                    .thenReturn(Optional.of(institution()));
            when(accountRepository.findByAccountNumberHash(any()))
                    .thenReturn(Optional.of(account(5L, USER_ID, BankAccountStatus.ACTIVE)));

            assertThatThrownBy(() -> service.linkAccount(
                    USER_ID, INST_CODE, ACCT_NUM, ACCT_NAME, null, true, IP))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode").isEqualTo("ACCOUNT_ALREADY_LINKED");

            verifyNoInteractions(adapterRegistry);
        }

        @Test
        @DisplayName("failed ownership verification → ValidationException + audit")
        void ownershipFailed() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
            when(institutionRepository.findByBankCodeAndActiveTrue(INST_CODE))
                    .thenReturn(Optional.of(institution()));
            when(accountRepository.findByAccountNumberHash(any())).thenReturn(Optional.empty());
            when(adapterRegistry.get(INST_CODE)).thenReturn(adapter);
            when(adapter.verifyOwnership(any()))
                    .thenReturn(AdapterResponse.failure(
                            "ACCOUNT_NAME_MISMATCH",
                            "Name does not match bank records",
                            "OWNERSHIP_CHECK"));

            assertThatThrownBy(() -> service.linkAccount(
                    USER_ID, INST_CODE, ACCT_NUM, "Wrong Name", null, true, IP))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo("OWNERSHIP_VERIFICATION_FAILED");

            verify(auditService).log(eq(USER_ID), eq("BANK_ACCOUNT_LINK_FAILED"),
                    any(), isNull(), any(), eq(IP));
            verify(accountRepository, never()).save(any());
        }
    }

    // ── listAccounts ───────────────────────────────────────────

    @Nested @DisplayName("listAccounts()")
    class ListAccounts {

        @Test
        @DisplayName("returns all accounts for the user regardless of status")
        void returnsAll() {
            List<BankAccount> accounts = List.of(
                    account(1L, USER_ID, BankAccountStatus.ACTIVE),
                    account(2L, USER_ID, BankAccountStatus.REMOVED));
            when(accountRepository.findByUserIdOrderByLinkedAtDesc(USER_ID))
                    .thenReturn(accounts);

            List<BankAccount> result = service.listAccounts(USER_ID);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("returns empty list when user has no accounts")
        void empty() {
            when(accountRepository.findByUserIdOrderByLinkedAtDesc(USER_ID))
                    .thenReturn(List.of());
            assertThat(service.listAccounts(USER_ID)).isEmpty();
        }
    }

    // ── setDefault ─────────────────────────────────────────────

    @Nested @DisplayName("setDefault()")
    class SetDefault {

        @Test
        @DisplayName("success – clears old default, sets new one, records consent")
        void success() {
            BankAccount account = account(10L, USER_ID, BankAccountStatus.ACTIVE);
            when(accountRepository.findByIdAndUserId(10L, USER_ID))
                    .thenReturn(Optional.of(account));
            when(accountRepository.clearDefaultForUser(USER_ID)).thenReturn(1);
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(consentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            BankAccount result = service.setDefault(10L, USER_ID, IP);

            assertThat(result.isDefault()).isTrue();
            verify(accountRepository).clearDefaultForUser(USER_ID);
            verify(consentRepository).save(argThat(c ->
                    c.getConsentType() == ConsentType.SET_DEFAULT));
            verify(auditService).log(eq(USER_ID), eq("BANK_ACCOUNT_SET_DEFAULT"),
                    any(), eq(10L), any(), eq(IP));
        }

        @Test
        @DisplayName("REMOVED account cannot be set as default")
        void removedCannotBeDefault() {
            BankAccount account = account(10L, USER_ID, BankAccountStatus.REMOVED);
            when(accountRepository.findByIdAndUserId(10L, USER_ID))
                    .thenReturn(Optional.of(account));

            assertThatThrownBy(() -> service.setDefault(10L, USER_ID, IP))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo("ACCOUNT_NOT_ACTIVE");
        }

        @Test
        @DisplayName("multiple accounts – only the targeted one becomes default")
        void onlyOneDefault() {
            BankAccount a1 = account(1L, USER_ID, BankAccountStatus.ACTIVE);
            BankAccount a2 = account(2L, USER_ID, BankAccountStatus.ACTIVE);
            a1.setDefault(true); // a1 was previously default

            when(accountRepository.findByIdAndUserId(2L, USER_ID))
                    .thenReturn(Optional.of(a2));
            when(accountRepository.clearDefaultForUser(USER_ID)).thenAnswer(inv -> {
                a1.setDefault(false); // simulate DB clearing
                return 1;
            });
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(consentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.setDefault(2L, USER_ID, IP);

            assertThat(a1.isDefault()).isFalse();
            assertThat(a2.isDefault()).isTrue();
        }
    }

    // ── removeAccount ──────────────────────────────────────────

    @Nested @DisplayName("removeAccount()")
    class RemoveAccount {

        @Test
        @DisplayName("success – status=REMOVED, receivingEnabled=false, consent + audit")
        void success() {
            BankAccount account = account(10L, USER_ID, BankAccountStatus.ACTIVE);
            account.setDefault(true);
            when(accountRepository.findByIdAndUserId(10L, USER_ID))
                    .thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(consentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.removeAccount(10L, USER_ID, true, IP);

            assertThat(account.getStatus()).isEqualTo(BankAccountStatus.REMOVED);
            assertThat(account.isReceivingEnabled()).isFalse();
            assertThat(account.isDefault()).isFalse();

            verify(consentRepository).save(argThat(c ->
                    c.getConsentType() == ConsentType.REMOVE_ACCOUNT
                    && c.isConsentGiven()));
            verify(auditService).log(eq(USER_ID), eq("BANK_ACCOUNT_REMOVED"),
                    any(), eq(10L), any(), eq(IP));
        }

        @Test
        @DisplayName("rejects without consent")
        void noConsent() {
            assertThatThrownBy(() -> service.removeAccount(10L, USER_ID, false, IP))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo("CONSENT_REQUIRED");
        }

        @Test
        @DisplayName("already removed → ConflictException")
        void alreadyRemoved() {
            BankAccount account = account(10L, USER_ID, BankAccountStatus.REMOVED);
            when(accountRepository.findByIdAndUserId(10L, USER_ID))
                    .thenReturn(Optional.of(account));

            assertThatThrownBy(() -> service.removeAccount(10L, USER_ID, true, IP))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode").isEqualTo("ACCOUNT_ALREADY_REMOVED");
        }
    }

    // ── Object-level authorization ─────────────────────────────

    @Nested @DisplayName("Object-level authorization")
    class ObjectLevelAuth {

        @Test
        @DisplayName("accessing another user's account → AuthorizationException")
        void crossUserAccessDenied() {
            // findByIdAndUserId returns empty for wrong user
            when(accountRepository.findByIdAndUserId(10L, OTHER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getAccount(10L, OTHER_ID))
                    .isInstanceOf(AuthorizationException.class)
                    .extracting("errorCode").isEqualTo("BANK_ACCOUNT_ACCESS_DENIED");
        }

        @Test
        @DisplayName("patching another user's account → AuthorizationException")
        void crossUserPatchDenied() {
            when(accountRepository.findByIdAndUserId(10L, OTHER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.patchAccount(
                    10L, OTHER_ID, "label", null, null, IP))
                    .isInstanceOf(AuthorizationException.class);
        }

        @Test
        @DisplayName("setDefault on another user's account → AuthorizationException")
        void crossUserDefaultDenied() {
            when(accountRepository.findByIdAndUserId(10L, OTHER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.setDefault(10L, OTHER_ID, IP))
                    .isInstanceOf(AuthorizationException.class);
        }

        @Test
        @DisplayName("removeAccount on another user's account → AuthorizationException")
        void crossUserRemoveDenied() {
            when(accountRepository.findByIdAndUserId(10L, OTHER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.removeAccount(10L, OTHER_ID, true, IP))
                    .isInstanceOf(AuthorizationException.class);
        }
    }

    // ── requireReceivableAccount ───────────────────────────────

    @Nested @DisplayName("requireReceivableAccount()")
    class ReceivableAccount {

        @Test
        @DisplayName("active account with receivingEnabled=true → ok")
        void canReceive() {
            BankAccount account = account(10L, USER_ID, BankAccountStatus.ACTIVE);
            when(accountRepository.findByIdAndUserId(10L, USER_ID))
                    .thenReturn(Optional.of(account));

            assertThatCode(() -> service.requireReceivableAccount(10L, USER_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("REMOVED account cannot receive")
        void removedCannotReceive() {
            BankAccount account = account(10L, USER_ID, BankAccountStatus.REMOVED);
            when(accountRepository.findByIdAndUserId(10L, USER_ID))
                    .thenReturn(Optional.of(account));

            assertThatThrownBy(() -> service.requireReceivableAccount(10L, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo("ACCOUNT_CANNOT_RECEIVE");
        }

        @Test
        @DisplayName("active account with receivingEnabled=false cannot receive")
        void disabledReceiving() {
            BankAccount account = account(10L, USER_ID, BankAccountStatus.ACTIVE);
            account.setReceivingEnabled(false);
            when(accountRepository.findByIdAndUserId(10L, USER_ID))
                    .thenReturn(Optional.of(account));

            assertThatThrownBy(() -> service.requireReceivableAccount(10L, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo("ACCOUNT_CANNOT_RECEIVE");
        }
    }

    // ── patchAccount ───────────────────────────────────────────

    @Nested @DisplayName("patchAccount()")
    class PatchAccount {

        @Test
        @DisplayName("updates label, discoverable, receivingEnabled independently")
        void partialUpdate() {
            BankAccount account = account(10L, USER_ID, BankAccountStatus.ACTIVE);
            when(accountRepository.findByIdAndUserId(10L, USER_ID))
                    .thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.patchAccount(10L, USER_ID, "New Label", false, null, IP);

            assertThat(account.getLabel()).isEqualTo("New Label");
            assertThat(account.isDiscoverable()).isFalse();
            assertThat(account.isReceivingEnabled()).isTrue(); // unchanged
        }

        @Test
        @DisplayName("enabling receivingEnabled writes RECEIVE_FUNDS consent")
        void enableReceivingWritesConsent() {
            BankAccount account = account(10L, USER_ID, BankAccountStatus.ACTIVE);
            account.setReceivingEnabled(false);
            when(accountRepository.findByIdAndUserId(10L, USER_ID))
                    .thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(consentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.patchAccount(10L, USER_ID, null, null, true, IP);

            verify(consentRepository).save(argThat(c ->
                    c.getConsentType() == ConsentType.RECEIVE_FUNDS));
        }
    }

    // ── sha256 helper ──────────────────────────────────────────

    @Test
    @DisplayName("sha256() is deterministic and produces non-empty output")
    void sha256Deterministic() {
        String h1 = BankAccountService.sha256("SIMULATED_BANK_A101001");
        String h2 = BankAccountService.sha256("SIMULATED_BANK_A101001");
        assertThat(h1).isEqualTo(h2).isNotBlank();
    }

    @Test
    @DisplayName("sha256() different inputs produce different hashes")
    void sha256Collision() {
        assertThat(BankAccountService.sha256("AAA"))
                .isNotEqualTo(BankAccountService.sha256("BBB"));
    }
}
