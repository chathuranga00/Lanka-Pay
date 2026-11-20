package com.lankapay.family;

import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.family.domain.*;
import com.lankapay.family.service.FamilyService;
import com.lankapay.family.store.InMemoryFamilyStore;
import com.lankapay.family.web.dto.*;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.notification.domain.NotificationType;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.registry.service.LookupService;
import com.lankapay.scheduled.domain.ScheduleFrequency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FamilyModuleTest {

    private InMemoryFamilyStore familyStore;
    private UserStore userStore;
    private AuditService auditService;
    private NotificationService notificationService;
    private FamilyService familyService;
    private LookupService lookupService;

    private static final Long GUARDIAN_ID = 101L;
    private static final Long STRANGER_ID = 202L;

    @BeforeEach
    void setUp() {
        familyStore = new InMemoryFamilyStore();
        userStore = mock(UserStore.class);
        auditService = mock(AuditService.class);
        notificationService = mock(NotificationService.class);
        lookupService = mock(LookupService.class);

        UserAccount guardianAccount = mock(UserAccount.class);
        when(guardianAccount.id()).thenReturn(GUARDIAN_ID);
        User guardianUser = mock(User.class);
        when(guardianUser.getId()).thenReturn(GUARDIAN_ID);
        when(guardianAccount.toUser()).thenReturn(guardianUser);
        when(userStore.findUserById(GUARDIAN_ID)).thenReturn(Optional.of(guardianAccount));

        UserAccount strangerAccount = mock(UserAccount.class);
        when(strangerAccount.id()).thenReturn(STRANGER_ID);
        User strangerUser = mock(User.class);
        when(strangerUser.getId()).thenReturn(STRANGER_ID);
        when(strangerAccount.toUser()).thenReturn(strangerUser);
        when(userStore.findUserById(STRANGER_ID)).thenReturn(Optional.of(strangerAccount));

        familyService = new FamilyService(familyStore, userStore, auditService, notificationService);
    }

    @Test
    @DisplayName("Create family member with data minimisation: first name only, age band, initials, audit logged, notification sent")
    void testCreateFamilyMember_DataMinimisation() {
        CreateFamilyMemberRequest req = new CreateFamilyMemberRequest(
                "Kasun Perera", // Contains surname; service must minimize to first name
                AgeBand.AGE_10_TO_13,
                null, // avatar initials auto-derived
                300_000L, // 3,000 LKR
                1_500_000L, // 15,000 LKR
                Set.of("FOOD", "EDUCATION"),
                new AllowanceScheduleDto(true, 50_000L, ScheduleFrequency.WEEKLY, LocalDate.now().plusDays(7), "Primary Account")
        );

        FamilyMemberDto created = familyService.createMember(GUARDIAN_ID, req, "192.168.1.1");

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotBlank();
        assertThat(created.guardianUserId()).isEqualTo(GUARDIAN_ID);
        // Data minimisation checks
        assertThat(created.firstName()).isEqualTo("Kasun"); // stripped surname
        assertThat(created.avatarInitials()).isEqualTo("KA");
        assertThat(created.ageBand()).isEqualTo(AgeBand.AGE_10_TO_13);
        assertThat(created.balanceMinor()).isEqualTo(0L);
        assertThat(created.dailyLimitMinor()).isEqualTo(300_000L);
        assertThat(created.monthlyLimitMinor()).isEqualTo(1_500_000L);
        assertThat(created.allowedCategories()).containsExactlyInAnyOrder("FOOD", "EDUCATION");
        assertThat(created.isFrozen()).isFalse();
        assertThat(created.allowanceSchedule().enabled()).isTrue();

        // Audit & Notification verified
        verify(auditService).log(eq(GUARDIAN_ID), eq("FAMILY_MEMBER_CREATED"), eq("FAMILY_MEMBER"), isNull(), anyString(), eq("192.168.1.1"));
        verify(notificationService).dispatch(any(User.class), eq(NotificationType.GENERAL), eq("Family Member Created"), anyString());
    }

    @Test
    @DisplayName("Access Control: Only guardian can view or modify the profile")
    void testAccessControl_OnlyGuardianCanSeeOrChange() {
        CreateFamilyMemberRequest req = new CreateFamilyMemberRequest(
                "Nimali",
                AgeBand.AGE_14_TO_17,
                "NM",
                500_000L,
                2_000_000L,
                Set.of("FOOD", "BOOKSTORE"),
                null
        );
        FamilyMemberDto created = familyService.createMember(GUARDIAN_ID, req, "127.0.0.1");

        // Guardian can view
        FamilyMemberDto fetched = familyService.getMember(GUARDIAN_ID, created.id());
        assertThat(fetched.firstName()).isEqualTo("Nimali");

        // Stranger CANNOT view
        assertThatThrownBy(() -> familyService.getMember(STRANGER_ID, created.id()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("Not authorized");

        // Stranger CANNOT update controls
        UpdateFamilyControlsRequest updateReq = new UpdateFamilyControlsRequest(600_000L, null, null, null, null);
        assertThatThrownBy(() -> familyService.updateControls(STRANGER_ID, created.id(), updateReq, "127.0.0.1"))
                .isInstanceOf(AuthorizationException.class);

        // Stranger CANNOT freeze
        assertThatThrownBy(() -> familyService.setFreeze(STRANGER_ID, created.id(), true, "127.0.0.1"))
                .isInstanceOf(AuthorizationException.class);

        // Stranger CANNOT view activity
        assertThatThrownBy(() -> familyService.getActivity(STRANGER_ID, created.id()))
                .isInstanceOf(AuthorizationException.class);

        // Stranger CANNOT delete
        assertThatThrownBy(() -> familyService.deleteMember(STRANGER_ID, created.id(), "127.0.0.1"))
                .isInstanceOf(AuthorizationException.class);
    }

    @Test
    @DisplayName("Funding: Only the guardian can fund the simulated balance; strangers are blocked")
    void testFunding_OnlyGuardianCanFund() {
        CreateFamilyMemberRequest req = new CreateFamilyMemberRequest("Amal", AgeBand.UNDER_10, "AM", 200_000L, 1_000_000L, null, null);
        FamilyMemberDto created = familyService.createMember(GUARDIAN_ID, req, "127.0.0.1");

        // Stranger tries to fund -> rejected
        FundFamilyMemberRequest fundReq = new FundFamilyMemberRequest(100_000L, "Stranger Bank", "Gift");
        assertThatThrownBy(() -> familyService.fundMember(STRANGER_ID, created.id(), fundReq, "127.0.0.1"))
                .isInstanceOf(AuthorizationException.class);

        // Guardian funds -> success
        FamilyMemberDto funded = familyService.fundMember(GUARDIAN_ID, created.id(), fundReq, "127.0.0.1");
        assertThat(funded.balanceMinor()).isEqualTo(100_000L);

        // Check activity has append-only FUNDING transaction
        List<FamilyTransactionDto> activity = familyService.getActivity(GUARDIAN_ID, created.id());
        assertThat(activity).hasSize(1);
        assertThat(activity.get(0).type()).isEqualTo(FamilyTransactionType.FUNDING);
        assertThat(activity.get(0).amountMinor()).isEqualTo(100_000L);
        assertThat(activity.get(0).balanceAfterMinor()).isEqualTo(100_000L);
        assertThat(activity.get(0).status()).isEqualTo(FamilyTransactionStatus.APPROVED);

        // Audit & Notification verified
        verify(auditService).log(eq(GUARDIAN_ID), eq("FAMILY_MEMBER_FUNDED"), eq("FAMILY_MEMBER"), isNull(), anyString(), eq("127.0.0.1"));
        verify(notificationService).dispatch(any(User.class), eq(NotificationType.GENERAL), eq("Family Balance Funded"), anyString());
    }

    @Test
    @DisplayName("Spending Limits Enforced: Daily limit exceeded blocks transaction and records declined entry")
    void testSpendingLimits_DailyLimitEnforced() {
        CreateFamilyMemberRequest req = new CreateFamilyMemberRequest("Saman", AgeBand.AGE_10_TO_13, "SA", 50_000L, 500_000L, Set.of("FOOD"), null);
        FamilyMemberDto created = familyService.createMember(GUARDIAN_ID, req, "127.0.0.1");

        // Fund with 200,000 LKR (minor)
        familyService.fundMember(GUARDIAN_ID, created.id(), new FundFamilyMemberRequest(200_000L, "Bank", "Funds"), "127.0.0.1");

        // Spend 60,000 (exceeds daily limit of 50,000)
        SpendFamilyMemberRequest spendReq = new SpendFamilyMemberRequest(60_000L, "FOOD", "Canteen", "Lunch");
        assertThatThrownBy(() -> familyService.spend(created.id(), spendReq, "127.0.0.1"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Daily spending limit exceeded");

        // Balance remains intact
        FamilyMemberDto member = familyService.getMember(GUARDIAN_ID, created.id());
        assertThat(member.balanceMinor()).isEqualTo(200_000L);

        // Activity contains DECLINED transaction
        List<FamilyTransactionDto> activity = familyService.getActivity(GUARDIAN_ID, created.id());
        assertThat(activity).anyMatch(t -> t.status() == FamilyTransactionStatus.DECLINED && t.declineReason().contains("Daily spending limit"));

        verify(auditService).log(eq(GUARDIAN_ID), eq("FAMILY_SPEND_DECLINED"), eq("FAMILY_MEMBER"), isNull(), anyString(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("Spending Category Restrictions: Unapproved category blocks transaction")
    void testSpendingCategoryRestrictions() {
        CreateFamilyMemberRequest req = new CreateFamilyMemberRequest("Kavindi", AgeBand.AGE_14_TO_17, "KV", 100_000L, 500_000L, Set.of("EDUCATION", "BOOKSTORE"), null);
        FamilyMemberDto created = familyService.createMember(GUARDIAN_ID, req, "127.0.0.1");
        familyService.fundMember(GUARDIAN_ID, created.id(), new FundFamilyMemberRequest(100_000L, "Bank", "Funds"), "127.0.0.1");

        // Attempt spending on unapproved category
        SpendFamilyMemberRequest spendReq = new SpendFamilyMemberRequest(10_000L, "ENTERTAINMENT", "Arcade Game", "Game");
        assertThatThrownBy(() -> familyService.spend(created.id(), spendReq, "127.0.0.1"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Category not allowed");

        // Attempt spending on allowed category -> succeeds
        SpendFamilyMemberRequest allowedReq = new SpendFamilyMemberRequest(10_000L, "BOOKSTORE", "Sarasavi", "Notebook");
        FamilyTransactionDto tx = familyService.spend(created.id(), allowedReq, "127.0.0.1");
        assertThat(tx.status()).isEqualTo(FamilyTransactionStatus.APPROVED);
        assertThat(tx.balanceAfterMinor()).isEqualTo(90_000L);

        verify(auditService).log(eq(GUARDIAN_ID), eq("FAMILY_SPEND_APPROVED"), eq("FAMILY_MEMBER"), isNull(), anyString(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("Freeze Control: When frozen, transactions are blocked")
    void testFreezeControl() {
        CreateFamilyMemberRequest req = new CreateFamilyMemberRequest("Ruwan", AgeBand.AGE_10_TO_13, "RW", 100_000L, 500_000L, Set.of("FOOD"), null);
        FamilyMemberDto created = familyService.createMember(GUARDIAN_ID, req, "127.0.0.1");
        familyService.fundMember(GUARDIAN_ID, created.id(), new FundFamilyMemberRequest(100_000L, "Bank", "Funds"), "127.0.0.1");

        // Freeze
        familyService.setFreeze(GUARDIAN_ID, created.id(), true, "127.0.0.1");

        SpendFamilyMemberRequest spendReq = new SpendFamilyMemberRequest(5_000L, "FOOD", "Snacks", "Juice");
        assertThatThrownBy(() -> familyService.spend(created.id(), spendReq, "127.0.0.1"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Account is frozen");

        // Unfreeze
        familyService.setFreeze(GUARDIAN_ID, created.id(), false, "127.0.0.1");
        FamilyTransactionDto tx = familyService.spend(created.id(), spendReq, "127.0.0.1");
        assertThat(tx.status()).isEqualTo(FamilyTransactionStatus.APPROVED);
    }

    @Test
    @DisplayName("No Discoverability: Child has no phone and cannot be found in lookup")
    void testNoDiscoverability() {
        CreateFamilyMemberRequest req = new CreateFamilyMemberRequest("Dilani", AgeBand.UNDER_10, "DI", 100_000L, 500_000L, Set.of("FOOD"), null);
        FamilyMemberDto created = familyService.createMember(GUARDIAN_ID, req, "127.0.0.1");

        // The family profile has NO phone number field at all
        assertThat(created).hasNoNullFieldsOrPropertiesExcept("status");

        // Registry lookup only takes phone numbers. Child's id or name cannot resolve in public registry.
        when(lookupService.lookup(eq(created.id()), any(), any())).thenThrow(new ResourceNotFoundException("IDENTITY", created.id()));
        assertThatThrownBy(() -> lookupService.lookup(created.id(), null, "127.0.0.1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Data Minimisation & Deletion: Profile removal purges data per retention rules")
    void testDeleteProfile_DataRetentionMinimisation() {
        CreateFamilyMemberRequest req = new CreateFamilyMemberRequest("Sajith", AgeBand.AGE_14_TO_17, "SJ", 100_000L, 500_000L, Set.of("FOOD"), null);
        FamilyMemberDto created = familyService.createMember(GUARDIAN_ID, req, "127.0.0.1");
        familyService.fundMember(GUARDIAN_ID, created.id(), new FundFamilyMemberRequest(50_000L, "Bank", "Gift"), "127.0.0.1");

        // Delete profile
        familyService.deleteMember(GUARDIAN_ID, created.id(), "127.0.0.1");

        // Verify profile is purged from store
        assertThatThrownBy(() -> familyService.getMember(GUARDIAN_ID, created.id()))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(familyService.listMembers(GUARDIAN_ID)).isEmpty();

        // Audit & Notification verified
        verify(auditService).log(eq(GUARDIAN_ID), eq("FAMILY_MEMBER_DELETED"), eq("FAMILY_MEMBER"), isNull(), anyString(), eq("127.0.0.1"));
        verify(notificationService).dispatch(any(User.class), eq(NotificationType.GENERAL), eq("Family Member Deleted"), anyString());
    }

    @Test
    @DisplayName("Child View: Returns minimal safe data without adult banking or geolocation")
    void testChildView() {
        CreateFamilyMemberRequest req = new CreateFamilyMemberRequest("Dinuka", AgeBand.AGE_10_TO_13, "DN", 50_000L, 200_000L, Set.of("FOOD", "EDUCATION"), null);
        FamilyMemberDto created = familyService.createMember(GUARDIAN_ID, req, "127.0.0.1");
        familyService.fundMember(GUARDIAN_ID, created.id(), new FundFamilyMemberRequest(100_000L, "Bank", "Pocket money"), "127.0.0.1");

        ChildViewDto childView = familyService.getChildView(GUARDIAN_ID, created.id());
        assertThat(childView.firstName()).isEqualTo("Dinuka");
        assertThat(childView.avatarInitials()).isEqualTo("DN");
        assertThat(childView.balanceMinor()).isEqualTo(100_000L);
        assertThat(childView.dailyLimitMinor()).isEqualTo(50_000L);
        assertThat(childView.remainingDailyMinor()).isEqualTo(50_000L);
        assertThat(childView.allowedCategories()).containsExactlyInAnyOrder("FOOD", "EDUCATION");
        assertThat(childView.isFrozen()).isFalse();
        assertThat(childView.recentTransactions()).isNotEmpty();
    }
}
