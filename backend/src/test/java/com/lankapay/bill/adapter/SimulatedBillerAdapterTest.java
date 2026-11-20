package com.lankapay.bill.adapter;

import com.lankapay.transfer.domain.FundingType;
import com.lankapay.transfer.domain.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimulatedBillerAdapter Unit Tests")
class SimulatedBillerAdapterTest {

    private SimulatedBillerAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SimulatedBillerAdapter();
        adapter.reset();
    }

    @Test
    @DisplayName("listBillers returns clearly fake billers with no real brand logos or company names")
    void listBillersReturnsClearlyFakeBillers() {
        List<BillerInfo> billers = adapter.listBillers();
        assertThat(billers).isNotEmpty();
        for (BillerInfo b : billers) {
            assertThat(b.name()).startsWith("Simulated ");
            assertThat(b.id()).startsWith("SIM_");
        }
    }

    @Test
    @DisplayName("getBiller returns biller if found and empty if not found")
    void getBillerLookup() {
        Optional<BillerInfo> found = adapter.getBiller("SIM_ELEC_01");
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Simulated Electricity Board");

        Optional<BillerInfo> missing = adapter.getBiller("NON_EXISTENT");
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("Scenario: bill found returns positive amount due and bill details")
    void scenarioBillFound() {
        BillDetails bill = adapter.fetchBill("SIM_ELEC_01", "1002345678");
        assertThat(bill.billStatus()).isEqualTo(BillStatus.BILL_FOUND);
        assertThat(bill.amountDue()).isGreaterThan(BigDecimal.ZERO);
        assertThat(bill.customerName()).isEqualTo("Simulated Customer");
        assertThat(bill.dueDate()).isNotNull();
    }

    @Test
    @DisplayName("Scenario: no bill due returns zero balance when customer reference is PAID or ends in 990")
    void scenarioNoBillDue() {
        BillDetails bill = adapter.fetchBill("SIM_WATER_01", "PAID-200345");
        assertThat(bill.billStatus()).isEqualTo(BillStatus.NO_BILL_DUE);
        assertThat(bill.amountDue()).isEqualByComparingTo(BigDecimal.ZERO);

        BillDetails bill2 = adapter.fetchBill("SIM_WATER_01", "1002345990");
        assertThat(bill2.billStatus()).isEqualTo(BillStatus.NO_BILL_DUE);
        assertThat(bill2.amountDue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Scenario: invalid reference fails validation and returns INVALID_REFERENCE")
    void scenarioInvalidReference() {
        BillValidationResult validation = adapter.validateCustomerReference("SIM_MOBI_01", "INVALID-REF");
        assertThat(validation.valid()).isFalse();
        assertThat(validation.errorMessage()).isNotEmpty();

        BillDetails bill = adapter.fetchBill("SIM_MOBI_01", "INVALID-REF");
        assertThat(bill.billStatus()).isEqualTo(BillStatus.INVALID_REFERENCE);
    }

    @Test
    @DisplayName("Scenario: amount changed returns updated amount due when reference contains CHANGED or ends in 991")
    void scenarioAmountChanged() {
        BillDetails bill = adapter.fetchBill("SIM_INET_01", "CHANGED-REF");
        assertThat(bill.billStatus()).isEqualTo(BillStatus.AMOUNT_CHANGED);
        assertThat(bill.amountDue()).isEqualByComparingTo(new BigDecimal("3850.00"));
    }

    @Test
    @DisplayName("Scenario: normal payBill returns COMPLETED status")
    void scenarioPayBillCompleted() {
        BillPaymentRequest req = new BillPaymentRequest(
                "PAY-REF-100",
                "SIM_ELEC_01",
                "1002345678",
                new BigDecimal("2500.00"),
                "LKR",
                FundingType.ACCOUNT,
                1L,
                "Test User"
        );
        BillPaymentResponse resp = adapter.payBill(req);
        assertThat(resp.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(resp.billerPaymentRef()).startsWith("SIM-BILL-");
    }

    @Test
    @DisplayName("Scenario: biller timeout but paid returns REQUIRES_RECONCILIATION and later query returns COMPLETED")
    void scenarioTimeoutButPaid() {
        BillPaymentRequest req = new BillPaymentRequest(
                "PAY-REF-TIMEOUT",
                "SIM_ELEC_01",
                "REF-TIMEOUT-992",
                new BigDecimal("1500.00"),
                "LKR",
                FundingType.ACCOUNT,
                1L,
                "Test User"
        );
        BillPaymentResponse resp = adapter.payBill(req);
        // Prompt rule: a timeout goes to REQUIRES_RECONCILIATION, never FAILED
        assertThat(resp.status()).isEqualTo(TransactionStatus.REQUIRES_RECONCILIATION);
        assertThat(resp.failureReason()).contains("BILLER_TIMEOUT");

        // When subsequently querying the biller payment:
        BillPaymentResponse queried = adapter.queryPayment("PAY-REF-TIMEOUT");
        assertThat(queried.status()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("Scenario: delayed confirmation returns PENDING and query resolves to COMPLETED")
    void scenarioDelayedConfirmation() {
        BillPaymentRequest req = new BillPaymentRequest(
                "PAY-REF-DELAY",
                "SIM_WATER_01",
                "REF-DELAY-993",
                new BigDecimal("800.00"),
                "LKR",
                FundingType.ACCOUNT,
                1L,
                "Test User"
        );
        BillPaymentResponse resp = adapter.payBill(req);
        assertThat(resp.status()).isEqualTo(TransactionStatus.PENDING);

        BillPaymentResponse queried = adapter.queryPayment("PAY-REF-DELAY");
        assertThat(queried.status()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("Scenario: hard failure returns FAILED with descriptive failure reason")
    void scenarioHardFailure() {
        BillPaymentRequest req = new BillPaymentRequest(
                "PAY-REF-FAIL",
                "SIM_MOBI_01",
                "REF-FAIL-994",
                new BigDecimal("500.00"),
                "LKR",
                FundingType.ACCOUNT,
                1L,
                "Test User"
        );
        BillPaymentResponse resp = adapter.payBill(req);
        assertThat(resp.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(resp.failureReason()).contains("BILLER_REJECTED");
    }

    @Test
    @DisplayName("Scenario: later reversal accepts payment but reconcile marks as REVERSED")
    void scenarioLaterReversal() {
        BillPaymentRequest req = new BillPaymentRequest(
                "PAY-REF-REV",
                "SIM_COUNCIL_01",
                "REF-REV-995",
                new BigDecimal("1200.00"),
                "LKR",
                FundingType.ACCOUNT,
                1L,
                "Test User"
        );
        BillPaymentResponse resp = adapter.payBill(req);
        assertThat(resp.status()).isEqualTo(TransactionStatus.COMPLETED);

        // Upon query or reconcile:
        BillPaymentResponse queried = adapter.queryPayment("PAY-REF-REV");
        assertThat(queried.status()).isEqualTo(TransactionStatus.REVERSED);

        BillerReconcileResult reconcile = adapter.reconcile("SIM_COUNCIL_01", LocalDate.now());
        assertThat(reconcile.items()).anyMatch(i -> i.paymentReference().equals("PAY-REF-REV") && i.status() == TransactionStatus.REVERSED);
    }
}
