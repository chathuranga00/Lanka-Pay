package com.lankapay.admin.service;

import com.lankapay.admin.web.dto.AdminTransferDetailResponse;
import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.User;
import com.lankapay.transfer.domain.TransactionEvent;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.Transfer;
import com.lankapay.transfer.domain.TransferInitiatedVia;
import com.lankapay.transfer.repository.TransactionEventRepository;
import com.lankapay.transfer.repository.TransferRepository;
import com.lankapay.transfer.service.ReconciliationService;
import com.lankapay.transfer.web.dto.ReconcileResultResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminTransferServiceTest {

    @Mock TransferRepository transferRepository;
    @Mock TransactionEventRepository transactionEventRepository;
    @Mock ReconciliationService reconciliationService;
    @Mock AuditService auditService;

    AdminTransferService service;
    Transfer transfer;
    User sender;
    User receiver;

    @BeforeEach
    void setUp() {
        service = new AdminTransferService(
                transferRepository,
                transactionEventRepository,
                reconciliationService,
                auditService
        );

        sender = new User("+94771111111", "Sender", "199011111111", "hash");
        ReflectionTestUtils.setField(sender, "id", 1L);

        receiver = new User("+94772222222", "Receiver", "199022222222", "hash");
        ReflectionTestUtils.setField(receiver, "id", 2L);

        transfer = new Transfer("TX-UUID-1", sender, receiver, new BigDecimal("1000.00"), "LKR", TransferInitiatedVia.APP);
        ReflectionTestUtils.setField(transfer, "id", 100L);
        ReflectionTestUtils.setField(transfer, "status", TransactionStatus.REQUIRES_RECONCILIATION);
        ReflectionTestUtils.setField(transfer, "createdAt", Instant.now());
    }

    @Test
    @DisplayName("Admin triggering reconciliation executes service and logs audit event")
    void triggerReconciliationAuditsAction() {
        when(transferRepository.findById(100L)).thenReturn(Optional.of(transfer));
        when(reconciliationService.reconcileTransfer(100L)).thenReturn(new ReconcileResultResponse(
                100L, "TX-UUID-1", TransactionStatus.REQUIRES_RECONCILIATION, TransactionStatus.COMPLETED, true, "OK"));

        ReconcileResultResponse res = service.triggerReconciliation(100L, 999L, "10.0.0.1");

        assertThat(res.resolved()).isTrue();
        assertThat(res.resolvedStatus()).isEqualTo(TransactionStatus.COMPLETED);

        verify(auditService).log(eq(999L), eq("ADMIN_TRIGGER_RECONCILIATION"), eq("TRANSFER"), eq(100L),
                contains("TX-UUID-1"), eq("10.0.0.1"));
    }

    @Test
    @DisplayName("Admin triggering bulk reconciliation executes service and logs audit event")
    void triggerBulkReconciliationAuditsAction() {
        when(reconciliationService.reconcileAllPending()).thenReturn(List.of());

        List<ReconcileResultResponse> res = service.triggerReconcileAll(999L, "10.0.0.1");

        assertThat(res).isNotNull();
        verify(auditService).log(eq(999L), eq("ADMIN_TRIGGER_RECONCILIATION_ALL"), eq("SYSTEM"), isNull(),
                contains("RECONCILE_ALL_PENDING"), eq("10.0.0.1"));
    }

    @Test
    @DisplayName("Admin cannot edit COMPLETED bank-confirmed transaction")
    void cannotEditCompletedTransaction() {
        ReflectionTestUtils.setField(transfer, "status", TransactionStatus.COMPLETED);
        when(transferRepository.findById(100L)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> service.validateNotBankConfirmed(100L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Bank-confirmed transactions cannot be modified by admins");
    }

    @Test
    @DisplayName("Admin cannot edit REVERSED bank-confirmed transaction")
    void cannotEditReversedTransaction() {
        ReflectionTestUtils.setField(transfer, "status", TransactionStatus.REVERSED);
        when(transferRepository.findById(100L)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> service.validateNotBankConfirmed(100L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Bank-confirmed transactions cannot be modified by admins");
    }

    @Test
    @DisplayName("Get transfer detail returns events history")
    void getTransferDetailReturnsEvents() {
        TransactionEvent ev = new TransactionEvent(transfer, null, TransactionStatus.PENDING, "INITIATED", null);
        ReflectionTestUtils.setField(ev, "id", 10L);

        when(transferRepository.findById(100L)).thenReturn(Optional.of(transfer));
        when(transactionEventRepository.findByTransferIdOrderByOccurredAtAsc(100L)).thenReturn(List.of(ev));

        AdminTransferDetailResponse detail = service.getTransferDetail(100L);

        assertThat(detail.transferId()).isEqualTo(100L);
        assertThat(detail.events()).hasSize(1);
        assertThat(detail.events().get(0).toStatus()).isEqualTo("PENDING");
    }
}
