package com.lankapay.admin.service;

import com.lankapay.admin.web.dto.AdminTransferDetailResponse;
import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.transfer.domain.*;
import com.lankapay.transfer.service.ReconciliationService;
import com.lankapay.transfer.web.dto.ReconcileResultResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Mock TransactionStore transactionStore;
    @Mock ReconciliationService reconciliationService;
    @Mock AuditService auditService;

    AdminTransferService service;
    TransactionRecord transfer;

    @BeforeEach
    void setUp() {
        service = new AdminTransferService(
                transactionStore,
                reconciliationService,
                auditService
        );

        transfer = new TransactionRecord(
                100L, "TX-UUID-1", "IDEM-1",
                1L, "+94771111111", "Sender",
                2L, "+94772222222", "Receiver",
                "SIMULATED_BANK_A", "SIMULATED_BANK_B",
                new BigDecimal("1000.00"), "LKR",
                TransactionStatus.REQUIRES_RECONCILIATION,
                null, null, null, null, false,
                TransferInitiatedVia.APP, null, 1L,
                Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("Admin triggering reconciliation executes service and logs audit event")
    void triggerReconciliationAuditsAction() {
        when(transactionStore.findTransferById(100L)).thenReturn(Optional.of(transfer));
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
        TransactionRecord completedTx = new TransactionRecord(
                100L, "TX-UUID-1", "IDEM-1",
                1L, "+94771111111", "Sender",
                2L, "+94772222222", "Receiver",
                "SIMULATED_BANK_A", "SIMULATED_BANK_B",
                new BigDecimal("1000.00"), "LKR",
                TransactionStatus.COMPLETED,
                null, null, null, null, false,
                TransferInitiatedVia.APP, null, 1L,
                Instant.now(), Instant.now()
        );
        when(transactionStore.findTransferById(100L)).thenReturn(Optional.of(completedTx));

        assertThatThrownBy(() -> service.validateNotBankConfirmed(100L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Bank-confirmed transactions cannot be modified by admins");
    }

    @Test
    @DisplayName("Admin cannot edit REVERSED bank-confirmed transaction")
    void cannotEditReversedTransaction() {
        TransactionRecord reversedTx = new TransactionRecord(
                100L, "TX-UUID-1", "IDEM-1",
                1L, "+94771111111", "Sender",
                2L, "+94772222222", "Receiver",
                "SIMULATED_BANK_A", "SIMULATED_BANK_B",
                new BigDecimal("1000.00"), "LKR",
                TransactionStatus.REVERSED,
                null, null, null, null, false,
                TransferInitiatedVia.APP, null, 1L,
                Instant.now(), Instant.now()
        );
        when(transactionStore.findTransferById(100L)).thenReturn(Optional.of(reversedTx));

        assertThatThrownBy(() -> service.validateNotBankConfirmed(100L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Bank-confirmed transactions cannot be modified by admins");
    }

    @Test
    @DisplayName("Get transfer detail returns events history")
    void getTransferDetailReturnsEvents() {
        TransactionEventRecord ev = new TransactionEventRecord(
                10L, 100L, null, TransactionStatus.PENDING, "INITIATED", null, Instant.now()
        );

        when(transactionStore.findTransferById(100L)).thenReturn(Optional.of(transfer));
        when(transactionStore.findEventsByTransferId(100L)).thenReturn(List.of(ev));

        AdminTransferDetailResponse detail = service.getTransferDetail(100L);

        assertThat(detail.transferId()).isEqualTo(100L);
        assertThat(detail.events()).hasSize(1);
        assertThat(detail.events().get(0).toStatus()).isEqualTo("PENDING");
    }
}
