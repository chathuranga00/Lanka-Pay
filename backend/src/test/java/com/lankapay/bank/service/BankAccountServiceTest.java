package com.lankapay.bank.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.adapter.AdapterResponse;
import com.lankapay.bank.adapter.BankAdapter;
import com.lankapay.bank.adapter.BankAdapterRegistry;
import com.lankapay.bank.crypto.AccountReferenceEncryptor;
import com.lankapay.bank.domain.*;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BankAccountService.
 * Collaborators are mocked.
 */
@ExtendWith(MockitoExtension.class)
class BankAccountServiceTest {

    @Mock LinkedAccountStore        linkedAccountStore;
    @Mock UserStore                 userStore;
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
                linkedAccountStore, userStore, adapterRegistry, encryptor, auditService);
    }

    UserAccount userAccount(Long id) {
        return new UserAccount(id, "+94771234567", "Kamal", "199012345678", "hash",
                UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now());
    }

    BankInstitutionRecord institution() {
        return new BankInstitutionRecord(1L, INST_CODE, "Simulated Bank A [SIMULATED]", true, true, Instant.now());
    }

    LinkedAccount linkedAccount(Long id, Long userId, BankAccountStatus status) {
        return new LinkedAccount(id, userId, INST_CODE,
                BankAccountService.sha256(INST_CODE + ACCT_NUM),
                "****1001", ACCT_NAME, null, true, true, "enc-ref", status, false, Instant.now(), Instant.now());
    }

    // ── linkAccount ────────────────────────────────────────────

    @Nested @DisplayName("linkAccount()")
    class LinkAccount {

        @Test
        @DisplayName("success – persists account, consent, audit")
        void success() {
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(linkedAccountStore.findActiveInstitutionByBankCode(INST_CODE))
                    .thenReturn(Optional.of(institution()));
            when(linkedAccountStore.findAccountByAccountNumberHash(any())).thenReturn(Optional.empty());
            when(adapterRegistry.get(INST_CODE)).thenReturn(adapter);
            when(adapter.verifyOwnership(any()))
                    .thenReturn(AdapterResponse.success("SIM-A-123", "OWNERSHIP_VERIFIED"));
            when(linkedAccountStore.saveAccount(any())).thenAnswer(inv -> {
                LinkedAccount a = inv.getArgument(0);
                return new LinkedAccount(10L, a.userId(), a.bankCode(), a.accountNumberHash(),
                        a.maskedNumber(), a.accountName(), a.label(), a.discoverable(),
                        a.receivingEnabled(), a.encryptedAccountReference(), a.status(),
                        a.isDefault(), Instant.now(), Instant.now());
            });
            when(linkedAccountStore.saveConsent(any())).thenAnswer(inv -> inv.getArgument(0));

            BankAccount result = service.linkAccount(
                    USER_ID, INST_CODE, ACCT_NUM, ACCT_NAME, "My Account", true, IP);

            assertThat(result.getMaskedNumber()).isEqualTo("****1001");
            assertThat(result.isActive()).isTrue();
            assertThat(result.isReceivingEnabled()).isTrue();
            assertThat(result.getLabel()).isEqualTo("My Account");

            assertThat(result.getEncryptedAccountReference()).isNotBlank();
            assertThat(result.getEncryptedAccountReference()).isNotEqualTo(ACCT_NUM);

            verify(linkedAccountStore).saveConsent(argThat(c ->
                    c.consentType() == ConsentType.LINK_ACCOUNT
                    && c.consentGiven()));
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
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(linkedAccountStore.findActiveInstitutionByBankCode(INST_CODE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.linkAccount(
                    USER_ID, INST_CODE, ACCT_NUM, ACCT_NAME, null, true, IP))
                    .isInstanceOf(com.lankapay.common.exception.ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("rejects duplicate account (same hash already linked)")
        void duplicateAccount() {
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(linkedAccountStore.findActiveInstitutionByBankCode(INST_CODE))
                    .thenReturn(Optional.of(institution()));
            when(linkedAccountStore.findAccountByAccountNumberHash(any()))
                    .thenReturn(Optional.of(linkedAccount(5L, USER_ID, BankAccountStatus.ACTIVE)));

            assertThatThrownBy(() -> service.linkAccount(
                    USER_ID, INST_CODE, ACCT_NUM, ACCT_NAME, null, true, IP))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode").isEqualTo("ACCOUNT_ALREADY_LINKED");

            verifyNoInteractions(adapterRegistry);
        }

        @Test
        @DisplayName("failed ownership verification -> ValidationException + audit")
        void ownershipFailed() {
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(linkedAccountStore.findActiveInstitutionByBankCode(INST_CODE))
                    .thenReturn(Optional.of(institution()));
            when(linkedAccountStore.findAccountByAccountNumberHash(any())).thenReturn(Optional.empty());
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
            verify(linkedAccountStore, never()).saveAccount(any());
        }
    }

    // ── listAccounts ───────────────────────────────────────────

    @Nested @DisplayName("listAccounts()")
    class ListAccounts {

        @Test
        @DisplayName("returns all accounts for the user regardless of status")
        void returnsAll() {
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            List<LinkedAccount> accounts = List.of(
                    linkedAccount(1L, USER_ID, BankAccountStatus.ACTIVE),
                    linkedAccount(2L, USER_ID, BankAccountStatus.REMOVED));
            when(linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(USER_ID))
                    .thenReturn(accounts);

            List<BankAccount> result = service.listAccounts(USER_ID);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("returns empty list when user has no accounts")
        void empty() {
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            when(linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(USER_ID))
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
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            LinkedAccount account = linkedAccount(10L, USER_ID, BankAccountStatus.ACTIVE);
            LinkedAccount updated = account.withDefault(true);
            when(linkedAccountStore.findAccountByIdAndUserId(10L, USER_ID))
                    .thenReturn(Optional.of(account));
            when(linkedAccountStore.setDefaultAccount(10L, USER_ID)).thenReturn(updated);
            when(linkedAccountStore.saveConsent(any())).thenAnswer(inv -> inv.getArgument(0));

            BankAccount result = service.setDefault(10L, USER_ID, IP);

            assertThat(result.isDefault()).isTrue();
            verify(linkedAccountStore).setDefaultAccount(10L, USER_ID);
            verify(linkedAccountStore).saveConsent(argThat(c ->
                    c.consentType() == ConsentType.SET_DEFAULT));
            verify(auditService).log(eq(USER_ID), eq("BANK_ACCOUNT_SET_DEFAULT"),
                    any(), eq(10L), any(), eq(IP));
        }

        @Test
        @DisplayName("REMOVED account cannot be set as default")
        void removedCannotBeDefault() {
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            LinkedAccount account = linkedAccount(10L, USER_ID, BankAccountStatus.REMOVED);
            when(linkedAccountStore.findAccountByIdAndUserId(10L, USER_ID))
                    .thenReturn(Optional.of(account));

            assertThatThrownBy(() -> service.setDefault(10L, USER_ID, IP))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo("ACCOUNT_NOT_ACTIVE");
        }
    }

    // ── removeAccount ──────────────────────────────────────────

    @Nested @DisplayName("removeAccount()")
    class RemoveAccount {

        @Test
        @DisplayName("success – status=REMOVED, receivingEnabled=false, consent + audit")
        void success() {
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            LinkedAccount account = linkedAccount(10L, USER_ID, BankAccountStatus.ACTIVE).withDefault(true);
            when(linkedAccountStore.findAccountByIdAndUserId(10L, USER_ID))
                    .thenReturn(Optional.of(account));
            when(linkedAccountStore.saveAccount(any())).thenAnswer(inv -> inv.getArgument(0));
            when(linkedAccountStore.saveConsent(any())).thenAnswer(inv -> inv.getArgument(0));

            service.removeAccount(10L, USER_ID, true, IP);

            verify(linkedAccountStore).saveAccount(argThat(a ->
                    a.status() == BankAccountStatus.REMOVED
                    && !a.receivingEnabled()
                    && !a.isDefault()));

            verify(linkedAccountStore).saveConsent(argThat(c ->
                    c.consentType() == ConsentType.REMOVE_ACCOUNT
                    && c.consentGiven()));
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
        @DisplayName("already removed -> ConflictException")
        void alreadyRemoved() {
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            LinkedAccount account = linkedAccount(10L, USER_ID, BankAccountStatus.REMOVED);
            when(linkedAccountStore.findAccountByIdAndUserId(10L, USER_ID))
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
        @DisplayName("accessing another user's account -> AuthorizationException")
        void crossUserAccessDenied() {
            when(userStore.findUserById(OTHER_ID)).thenReturn(Optional.of(userAccount(OTHER_ID)));
            when(linkedAccountStore.findAccountByIdAndUserId(10L, OTHER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getAccount(10L, OTHER_ID))
                    .isInstanceOf(AuthorizationException.class)
                    .extracting("errorCode").isEqualTo("BANK_ACCOUNT_ACCESS_DENIED");
        }

        @Test
        @DisplayName("patching another user's account -> AuthorizationException")
        void crossUserPatchDenied() {
            when(userStore.findUserById(OTHER_ID)).thenReturn(Optional.of(userAccount(OTHER_ID)));
            when(linkedAccountStore.findAccountByIdAndUserId(10L, OTHER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.patchAccount(
                    10L, OTHER_ID, "label", null, null, IP))
                    .isInstanceOf(AuthorizationException.class);
        }

        @Test
        @DisplayName("setDefault on another user's account -> AuthorizationException")
        void crossUserDefaultDenied() {
            when(userStore.findUserById(OTHER_ID)).thenReturn(Optional.of(userAccount(OTHER_ID)));
            when(linkedAccountStore.findAccountByIdAndUserId(10L, OTHER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.setDefault(10L, OTHER_ID, IP))
                    .isInstanceOf(AuthorizationException.class);
        }

        @Test
        @DisplayName("removeAccount on another user's account -> AuthorizationException")
        void crossUserRemoveDenied() {
            when(userStore.findUserById(OTHER_ID)).thenReturn(Optional.of(userAccount(OTHER_ID)));
            when(linkedAccountStore.findAccountByIdAndUserId(10L, OTHER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.removeAccount(10L, OTHER_ID, true, IP))
                    .isInstanceOf(AuthorizationException.class);
        }
    }

    // ── requireReceivableAccount ───────────────────────────────

    @Nested @DisplayName("requireReceivableAccount()")
    class ReceivableAccount {

        @Test
        @DisplayName("active account with receivingEnabled=true -> ok")
        void canReceive() {
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            LinkedAccount account = linkedAccount(10L, USER_ID, BankAccountStatus.ACTIVE);
            when(linkedAccountStore.findAccountByIdAndUserId(10L, USER_ID))
                    .thenReturn(Optional.of(account));

            assertThatCode(() -> service.requireReceivableAccount(10L, USER_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("REMOVED account cannot receive")
        void removedCannotReceive() {
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            LinkedAccount account = linkedAccount(10L, USER_ID, BankAccountStatus.REMOVED);
            when(linkedAccountStore.findAccountByIdAndUserId(10L, USER_ID))
                    .thenReturn(Optional.of(account));

            assertThatThrownBy(() -> service.requireReceivableAccount(10L, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo("ACCOUNT_CANNOT_RECEIVE");
        }

        @Test
        @DisplayName("active account with receivingEnabled=false cannot receive")
        void disabledReceiving() {
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            LinkedAccount account = linkedAccount(10L, USER_ID, BankAccountStatus.ACTIVE).withReceivingEnabled(false);
            when(linkedAccountStore.findAccountByIdAndUserId(10L, USER_ID))
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
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            LinkedAccount account = linkedAccount(10L, USER_ID, BankAccountStatus.ACTIVE);
            when(linkedAccountStore.findAccountByIdAndUserId(10L, USER_ID))
                    .thenReturn(Optional.of(account));
            when(linkedAccountStore.saveAccount(any())).thenAnswer(inv -> inv.getArgument(0));

            service.patchAccount(10L, USER_ID, "New Label", false, null, IP);

            verify(linkedAccountStore).saveAccount(argThat(a ->
                    "New Label".equals(a.label()) && !a.discoverable() && a.receivingEnabled()));
        }

        @Test
        @DisplayName("enabling receivingEnabled writes RECEIVE_FUNDS consent")
        void enableReceivingWritesConsent() {
            when(userStore.findUserById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID)));
            LinkedAccount account = linkedAccount(10L, USER_ID, BankAccountStatus.ACTIVE).withReceivingEnabled(false);
            when(linkedAccountStore.findAccountByIdAndUserId(10L, USER_ID))
                    .thenReturn(Optional.of(account));
            when(linkedAccountStore.saveAccount(any())).thenAnswer(inv -> inv.getArgument(0));
            when(linkedAccountStore.saveConsent(any())).thenAnswer(inv -> inv.getArgument(0));

            service.patchAccount(10L, USER_ID, null, null, true, IP);

            verify(linkedAccountStore).saveConsent(argThat(c ->
                    c.consentType() == ConsentType.RECEIVE_FUNDS));
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
