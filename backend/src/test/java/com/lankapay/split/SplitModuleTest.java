package com.lankapay.split;

import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.notification.service.NotificationService;
import com.lankapay.qr.domain.PaymentRequestStatus;
import com.lankapay.qr.domain.QrStore;
import com.lankapay.qr.service.PaymentRequestService;
import com.lankapay.qr.web.dto.CreatePaymentRequestDto;
import com.lankapay.qr.web.dto.PaymentRequestResponse;
import com.lankapay.split.domain.*;
import com.lankapay.split.service.SplitService;
import com.lankapay.split.store.InMemorySplitStore;
import com.lankapay.split.web.dto.*;
import com.lankapay.transfer.service.TransferService;
import com.lankapay.transfer.web.dto.InitiateTransferRequest;
import com.lankapay.transfer.web.dto.TransferResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SplitModuleTest {

    private InMemorySplitStore splitStore;
    @Mock private UserStore userStore;
    @Mock private PaymentRequestService paymentRequestService;
    @Mock private TransferService transferService;
    @Mock private NotificationService notificationService;
    @Mock private QrStore qrStore;

    private SplitService splitService;

    private User owner;
    private User participant1;
    private User participant2;
    private User outsider;

    @BeforeEach
    void setUp() {
        splitStore = new InMemorySplitStore();
        splitService = new SplitService(
                splitStore,
                userStore,
                paymentRequestService,
                transferService,
                notificationService,
                qrStore
        );

        owner = new User("+94771111111", "Owner User", "199011111111", "$2a$12$hash");
        owner.setId(101L);
        ReflectionTestUtils.setField(owner, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(owner, "role", UserRole.CUSTOMER);

        participant1 = new User("+94772222222", "Participant One", "199022222222", "$2a$12$hash");
        participant1.setId(102L);
        ReflectionTestUtils.setField(participant1, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(participant1, "role", UserRole.CUSTOMER);

        participant2 = new User("+94773333333", "Participant Two", "199033333333", "$2a$12$hash");
        participant2.setId(103L);
        ReflectionTestUtils.setField(participant2, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(participant2, "role", UserRole.CUSTOMER);

        outsider = new User("+94779999999", "Outsider User", "199099999999", "$2a$12$hash");
        outsider.setId(999L);
        ReflectionTestUtils.setField(outsider, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(outsider, "role", UserRole.CUSTOMER);

        // Setup mock user accounts
        UserAccount ownerAcc = new UserAccount(owner.getId(), owner.getPhone(), owner.getFullName(), owner.getNicNumber(), "$2a$12$hash", UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now());
        UserAccount p1Acc = new UserAccount(participant1.getId(), participant1.getPhone(), participant1.getFullName(), participant1.getNicNumber(), "$2a$12$hash", UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now());
        UserAccount p2Acc = new UserAccount(participant2.getId(), participant2.getPhone(), participant2.getFullName(), participant2.getNicNumber(), "$2a$12$hash", UserStatus.ACTIVE, UserRole.CUSTOMER, 0, null, Instant.now(), Instant.now());

        lenient().when(userStore.findUserById(101L)).thenReturn(Optional.of(ownerAcc));
        lenient().when(userStore.findUserById(102L)).thenReturn(Optional.of(p1Acc));
        lenient().when(userStore.findUserById(103L)).thenReturn(Optional.of(p2Acc));
        lenient().when(userStore.findUserByPhone("+94771111111")).thenReturn(Optional.of(ownerAcc));
        lenient().when(userStore.findUserByPhone("+94772222222")).thenReturn(Optional.of(p1Acc));
        lenient().when(userStore.findUserByPhone("+94773333333")).thenReturn(Optional.of(p2Acc));
    }

    private PaymentRequestResponse mockPaymentRequest(String ref) {
        return new PaymentRequestResponse(
                1L, ref, 101L, "Kamal", 102L, "Nimal",
                new BigDecimal("20.00"), "LKR", "Share",
                PaymentRequestStatus.PENDING, "QR-" + ref, "PAYLOAD-" + ref,
                null, Instant.now().plusSeconds(3600), Instant.now()
        );
    }

    @Test
    @DisplayName("1. Exact sums with remainders: Equal split allocates integer remainder to owner")
    void testExactSumsWithRoundingRemainderGoesToOwner() {
        // Total = 1000 minor units (LKR 10.00). 2 participants + 1 owner = 3 people.
        // 1000 / 3 = 333 per person.
        // Remainder = 1000 - (333 * 3) = 1.
        // Each participant pays 333 minor.
        // Owner pays 333 + 1 = 334 minor.
        when(paymentRequestService.createPaymentRequest(any(CreatePaymentRequestDto.class), eq(owner)))
                .thenReturn(mockPaymentRequest("PREQ-1"));

        CreateSplitRequest request = new CreateSplitRequest(
                "Lunch at Dutch Hospital",
                1000L,
                "LKR",
                "EQUAL",
                List.of(
                        new SplitParticipantRequest(102L, null, null),
                        new SplitParticipantRequest(103L, null, null)
                ),
                48
        );

        SplitResponse split = splitService.createSplit(request, owner);

        assertThat(split.id()).isNotNull();
        assertThat(split.totalMinor()).isEqualTo(1000L);
        assertThat(split.ownerShareMinor()).isEqualTo(334L);
        assertThat(split.participants()).hasSize(2);
        assertThat(split.participants().get(0).shareMinor()).isEqualTo(333L);
        assertThat(split.participants().get(1).shareMinor()).isEqualTo(333L);

        // Sum verification: 334 + 333 + 333 = 1000
        long totalSum = split.ownerShareMinor() + split.participants().get(0).shareMinor() + split.participants().get(1).shareMinor();
        assertThat(totalSum).isEqualTo(1000L);
    }

    @Test
    @DisplayName("2. Partial payment & progress: Split updates status only after transfer succeeds and marks completed when all pay")
    void testPartialPaymentAndProgress() {
        when(paymentRequestService.createPaymentRequest(any(CreatePaymentRequestDto.class), eq(owner)))
                .thenReturn(mockPaymentRequest("PREQ-P"));

        CreateSplitRequest request = new CreateSplitRequest(
                "Weekend Trip",
                6000L,
                "LKR",
                "EQUAL",
                List.of(
                        new SplitParticipantRequest(102L, null, null),
                        new SplitParticipantRequest(103L, null, null)
                ),
                48
        );
        SplitResponse split = splitService.createSplit(request, owner);
        String splitId = split.id();

        // Initially: 0 of 2 paid, ACTIVE
        assertThat(split.paidCount()).isEqualTo(0);
        assertThat(split.status()).isEqualTo(SplitStatus.ACTIVE);

        // Participant 1 pays
        TransferResponse tx1 = mock(TransferResponse.class);
        when(tx1.transferId()).thenReturn(501L);
        when(transferService.initiateTransfer(any(InitiateTransferRequest.class), any(User.class), anyString(), anyString(), anyString()))
                .thenReturn(tx1);

        PaySplitRequest payReq1 = new PaySplitRequest("1234", null, "IDEMP-1");
        SplitResponse afterP1 = splitService.payShare(splitId, participant1.getId(), payReq1);

        // 1 of 2 paid, still ACTIVE
        assertThat(afterP1.paidCount()).isEqualTo(1);
        assertThat(afterP1.status()).isEqualTo(SplitStatus.ACTIVE);
        assertThat(afterP1.participants().stream().filter(p -> p.userId().equals(102L)).findFirst().get().status())
                .isEqualTo(ParticipantStatus.PAID);

        // Participant 2 pays
        TransferResponse tx2 = mock(TransferResponse.class);
        when(tx2.transferId()).thenReturn(502L);
        when(transferService.initiateTransfer(any(InitiateTransferRequest.class), any(User.class), anyString(), anyString(), anyString()))
                .thenReturn(tx2);

        PaySplitRequest payReq2 = new PaySplitRequest("1234", null, "IDEMP-2");
        SplitResponse afterP2 = splitService.payShare(splitId, participant2.getId(), payReq2);

        // All paid -> COMPLETED
        assertThat(afterP2.paidCount()).isEqualTo(2);
        assertThat(afterP2.status()).isEqualTo(SplitStatus.COMPLETED);
    }

    @Test
    @DisplayName("3. Expiry enforcement: Expired split cannot be paid")
    void testSplitExpiryEnforcement() {
        when(paymentRequestService.createPaymentRequest(any(CreatePaymentRequestDto.class), eq(owner)))
                .thenReturn(mockPaymentRequest("PREQ-E"));

        CreateSplitRequest request = new CreateSplitRequest(
                "Coffee Meetup",
                3000L,
                "LKR",
                "EQUAL",
                List.of(new SplitParticipantRequest(102L, null, null)),
                24
        );
        SplitResponse split = splitService.createSplit(request, owner);

        // Force expiration
        BillSplit entity = splitStore.findById(split.id()).get();
        entity.setExpiresAt(Instant.now().minus(2, ChronoUnit.HOURS));
        splitStore.save(entity);

        // Attempting to pay expired split throws ValidationException
        PaySplitRequest payReq = new PaySplitRequest("1234", null, null);
        assertThatThrownBy(() -> splitService.payShare(split.id(), participant1.getId(), payReq))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("4. Participant decline: Participant declining updates status to DECLINED")
    void testParticipantDecline() {
        when(paymentRequestService.createPaymentRequest(any(CreatePaymentRequestDto.class), eq(owner)))
                .thenReturn(mockPaymentRequest("PREQ-D"));

        CreateSplitRequest request = new CreateSplitRequest(
                "Concert Tickets",
                5000L,
                "LKR",
                "EQUAL",
                List.of(new SplitParticipantRequest(102L, null, null)),
                48
        );
        SplitResponse split = splitService.createSplit(request, owner);

        SplitResponse declined = splitService.declineShare(split.id(), participant1.getId());

        SplitParticipantResponse pResp = declined.participants().get(0);
        assertThat(pResp.status()).isEqualTo(ParticipantStatus.DECLINED);
    }

    @Test
    @DisplayName("5. Unauthorized access: Outsider cannot view or manipulate split (IDOR protection)")
    void testUnauthorizedAccess_IDORProtection() {
        when(paymentRequestService.createPaymentRequest(any(CreatePaymentRequestDto.class), eq(owner)))
                .thenReturn(mockPaymentRequest("PREQ-S"));

        CreateSplitRequest request = new CreateSplitRequest(
                "Private Split",
                4000L,
                "LKR",
                "EQUAL",
                List.of(new SplitParticipantRequest(102L, null, null)),
                48
        );
        SplitResponse split = splitService.createSplit(request, owner);

        // Outsider attempts GET
        assertThatThrownBy(() -> splitService.getSplit(split.id(), outsider.getId()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("authorization");

        // Outsider attempts cancel
        assertThatThrownBy(() -> splitService.cancelSplit(split.id(), outsider.getId()))
                .isInstanceOf(AuthorizationException.class);

        // Non-owner (even participant) attempts cancel
        assertThatThrownBy(() -> splitService.cancelSplit(split.id(), participant1.getId()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("Only the split owner");
    }

    @Test
    @DisplayName("6. Duplicate pay: Cannot pay twice for the same split share")
    void testDuplicatePayProtection() {
        when(paymentRequestService.createPaymentRequest(any(CreatePaymentRequestDto.class), eq(owner)))
                .thenReturn(mockPaymentRequest("PREQ-DUP"));

        CreateSplitRequest request = new CreateSplitRequest(
                "Dinner Split",
                3000L,
                "LKR",
                "EQUAL",
                List.of(new SplitParticipantRequest(102L, null, null)),
                48
        );
        SplitResponse split = splitService.createSplit(request, owner);

        TransferResponse tx = mock(TransferResponse.class);
        when(tx.transferId()).thenReturn(999L);
        when(transferService.initiateTransfer(any(InitiateTransferRequest.class), any(User.class), anyString(), anyString(), anyString()))
                .thenReturn(tx);

        PaySplitRequest payReq = new PaySplitRequest("1234", null, "IDEMP-DUP");
        splitService.payShare(split.id(), participant1.getId(), payReq);

        // Second payment attempt
        assertThatThrownBy(() -> splitService.payShare(split.id(), participant1.getId(), payReq))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already paid");
    }

    @Test
    @DisplayName("7. Owner cancels unpaid shares")
    void testOwnerCancelsUnpaidShares() {
        when(paymentRequestService.createPaymentRequest(any(CreatePaymentRequestDto.class), eq(owner)))
                .thenReturn(mockPaymentRequest("PREQ-C"));

        CreateSplitRequest request = new CreateSplitRequest(
                "Taxi Fare",
                2000L,
                "LKR",
                "EQUAL",
                List.of(new SplitParticipantRequest(102L, null, null)),
                48
        );
        SplitResponse split = splitService.createSplit(request, owner);

        SplitResponse cancelled = splitService.cancelSplit(split.id(), owner.getId());

        assertThat(cancelled.status()).isEqualTo(SplitStatus.CANCELLED);
        assertThat(cancelled.participants().get(0).status()).isEqualTo(ParticipantStatus.CANCELLED);
    }

    @Test
    @DisplayName("8. Reminder rate limiting: One reminder per participant per 24 hours")
    void testReminderRateLimiting_24HourRule() {
        when(paymentRequestService.createPaymentRequest(any(CreatePaymentRequestDto.class), eq(owner)))
                .thenReturn(mockPaymentRequest("PREQ-R"));

        CreateSplitRequest request = new CreateSplitRequest(
                "Movie Outing",
                3000L,
                "LKR",
                "EQUAL",
                List.of(new SplitParticipantRequest(102L, null, null)),
                48
        );
        SplitResponse split = splitService.createSplit(request, owner);

        // First reminder succeeds
        SplitResponse reminded1 = splitService.remindParticipant(split.id(), participant1.getId(), owner.getId());
        SplitParticipantResponse p = reminded1.participants().get(0);
        assertThat(p.lastRemindedAt()).isNotNull();
        assertThat(p.canRemind()).isFalse();

        // Immediate second reminder fails due to 24h rate limit
        assertThatThrownBy(() -> splitService.remindParticipant(split.id(), participant1.getId(), owner.getId()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("24 hours");
    }

    @Test
    @DisplayName("9. Max 10 participants constraint")
    void testMax10ParticipantsConstraint() {
        List<SplitParticipantRequest> list = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            list.add(new SplitParticipantRequest((long) (200 + i), "+947700000" + i, null));
        }

        CreateSplitRequest request = new CreateSplitRequest("Large Group", 10000L, "LKR", "EQUAL", list, 48);

        assertThatThrownBy(() -> splitService.createSplit(request, owner))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("maximum of 10 participants");
    }

    @Test
    @DisplayName("10. Participants must be registered users")
    void testParticipantsMustBeRegisteredUsers() {
        when(userStore.findUserByPhone("+94778888888")).thenReturn(Optional.empty());

        CreateSplitRequest request = new CreateSplitRequest(
                "Party",
                5000L,
                "LKR",
                "EQUAL",
                List.of(new SplitParticipantRequest(null, "+94778888888", null)),
                48
        );

        assertThatThrownBy(() -> splitService.createSplit(request, owner))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not registered");
    }
}
