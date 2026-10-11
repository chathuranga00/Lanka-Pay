package com.lankapay.notification.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.adapter.*;
import com.lankapay.bank.domain.BankAccount;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.Transfer;
import com.lankapay.transfer.domain.TransferInitiatedVia;
import com.lankapay.transfer.repository.TransactionEventRepository;
import com.lankapay.transfer.repository.TransferRepository;
import com.lankapay.transfer.service.DailyLimitService;
import com.lankapay.transfer.service.TransferProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryFailureTest {

    @Mock TransferRepository transferRepository;
    @Mock TransactionEventRepository transactionEventRepository;
    @Mock BankAdapterRegistry bankAdapterRegistry;
    @Mock DailyLimitService dailyLimitService;
    @Mock AuditService auditService;
    @Mock NotificationService notificationService;
    @Mock BankAdapter bankAdapter;

    TransferProcessor transferProcessor;
    User sender;
    User receiver;
    BankAccount senderAccount;
    BankAccount receiverAccount;

    @BeforeEach
    void setUp() {
        transferProcessor = new TransferProcessor(
                transferRepository,
                transactionEventRepository,
                bankAdapterRegistry,
                dailyLimitService,
                auditService,
                notificationService
        );

        sender = new User("+94771234567", "Kamal Perera", "199012345678", "$2a$12$hash");
        ReflectionTestUtils.setField(sender, "id", 1L);
        ReflectionTestUtils.setField(sender, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(sender, "role", UserRole.CUSTOMER);

        receiver = new User("+94779876543", "Nimal Silva", "199587654321", "$2a$12$hash");
        ReflectionTestUtils.setField(receiver, "id", 2L);
        ReflectionTestUtils.setField(receiver, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(receiver, "role", UserRole.CUSTOMER);

        senderAccount = mock(BankAccount.class);
        when(senderAccount.getBankCode()).thenReturn("BOC");
        when(senderAccount.getMaskedNumber()).thenReturn("****1234");

        receiverAccount = mock(BankAccount.class);
        when(receiverAccount.getBankCode()).thenReturn("COMB");
        when(receiverAccount.getMaskedNumber()).thenReturn("****5678");

        when(bankAdapterRegistry.get("BOC")).thenReturn(bankAdapter);
    }

    @Test
    @DisplayName("Notification delivery failure does NOT affect transaction outcome: payment still completes successfully")
    void testNotificationDeliveryFailureDoesNotAffectTransactionOutcome() {
        // Arrange
        Transfer transfer = new Transfer(
                UUID.randomUUID().toString(),
                sender,
                receiver,
                new BigDecimal("5000.00"),
                "LKR",
                TransferInitiatedVia.APP
        );
        ReflectionTestUtils.setField(transfer, "id", 101L);

        when(transferRepository.save(any(Transfer.class))).thenAnswer(i -> i.getArgument(0));

        // BankAdapter pre-flight eligibility check succeeds
        when(bankAdapter.checkTransferEligibility(any(TransferEligibilityRequest.class)))
                .thenReturn(AdapterResponse.success("elig-ref", "ELIGIBLE"));

        // BankAdapter confirms successful debit and transfer
        when(bankAdapter.initiateTransfer(any(TransferRequest.class)))
                .thenReturn(AdapterResponse.success("BOC-TX-9988", "PROV-REF-123"));

        // SIMULATE CRITICAL NOTIFICATION FAILURE: NotificationService throws RuntimeException
        doThrow(new RuntimeException("Simulated Push Gateway 504 Gateway Timeout: connection reset by peer"))
                .when(notificationService).notifyPaymentSent(any(Transfer.class));

        // Act
        Transfer result = transferProcessor.process(
                transfer, senderAccount, receiverAccount, null, "127.0.0.1");

        // Assert
        // 1. Transfer status must be COMPLETED
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(result.getBankTxRef()).isEqualTo("BOC-TX-9988");

        // 2. Daily limit spend must still be recorded
        verify(dailyLimitService).recordSpend(sender, new BigDecimal("5000.00"));

        // 3. Audit trail must record TRANSFER_COMPLETED
        verify(auditService).log(eq(1L), eq("TRANSFER_COMPLETED"), eq("TRANSFER"), eq(101L), anyString(), eq("127.0.0.1"));

        // 4. Notification service was indeed invoked despite the failure
        verify(notificationService).notifyPaymentSent(any(Transfer.class));
    }
}
