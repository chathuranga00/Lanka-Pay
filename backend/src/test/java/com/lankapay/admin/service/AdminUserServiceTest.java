package com.lankapay.admin.service;

import com.lankapay.admin.web.dto.AdminUserDetailResponse;
import com.lankapay.admin.web.dto.UpdateUserStatusRequest;
import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock UserStore userStore;
    @Mock LinkedAccountStore linkedAccountStore;
    @Mock AuditService auditService;

    AdminUserService service;
    UserAccount user;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userStore, linkedAccountStore, auditService);

        user = new UserAccount(1L, "+94771234567", "Kamal Perera", "199012345678", "$2a$12$secretHashNotExposed",
                UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("Admin user detail never exposes password hash, OTPs or tokens")
    void userDetailDoesNotExposeSecrets() {
        LinkedAccount acct = new LinkedAccount(10L, 1L, "SIMULATED_BANK_A", "hash", "****1234", "Kamal Acct",
                null, true, true, "encRef", BankAccountStatus.ACTIVE, false, Instant.now(), Instant.now());

        when(userStore.findUserById(1L)).thenReturn(Optional.of(user));
        when(linkedAccountStore.findAccountsByUserIdOrderByLinkedAtDesc(1L)).thenReturn(List.of(acct));

        AdminUserDetailResponse detail = service.getUserDetail(1L);

        assertThat(detail.userId()).isEqualTo(1L);
        assertThat(detail.fullName()).isEqualTo("Kamal Perera");
        assertThat(detail.maskedPhone()).isEqualTo("+9477***4567");
        assertThat(detail.maskedNic()).isEqualTo("1990****");
        assertThat(detail.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(detail.linkedAccounts()).hasSize(1);
        assertThat(detail.linkedAccounts().get(0).maskedNumber()).isEqualTo("****1234");

        // Assert record has no password or credentials getters
        assertThat(detail.toString()).doesNotContain("secretHashNotExposed");
    }

    @Test
    @DisplayName("Freezing user updates status and creates audit log entry")
    void freezeUserCreatesAuditEntry() {
        when(userStore.findUserById(1L)).thenReturn(Optional.of(user));
        when(userStore.saveUser(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateUserStatusRequest req = new UpdateUserStatusRequest(UserStatus.FROZEN, "Suspected fraud");

        AdminUserDetailResponse res = service.updateUserStatus(1L, req, 999L, "10.0.0.1");

        assertThat(res.status()).isEqualTo(UserStatus.FROZEN);

        verify(auditService).log(eq(999L), eq("ADMIN_USER_STATUS_CHANGE"), eq("USER"), eq(1L),
                contains("FROZEN"), eq("10.0.0.1"));
    }

    @Test
    @DisplayName("Attempting to set user status to DELETED directly throws ValidationException")
    void cannotSetDeletedDirectly() {
        when(userStore.findUserById(1L)).thenReturn(Optional.of(user));

        UpdateUserStatusRequest req = new UpdateUserStatusRequest(UserStatus.DELETED, "Remove user");

        assertThatThrownBy(() -> service.updateUserStatus(1L, req, 999L, "10.0.0.1"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Admins cannot directly mark users as DELETED");

        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }
}
