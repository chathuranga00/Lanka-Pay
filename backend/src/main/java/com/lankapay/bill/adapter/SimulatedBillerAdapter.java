package com.lankapay.bill.adapter;

import com.lankapay.transfer.domain.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SimulatedBillerAdapter with clearly fake billers and deterministic scenarios
 * keyed by customer reference.
 *
 * NOTE: Strictly uses fake institutional names. No real company names or logos are used.
 */
@Component
public class SimulatedBillerAdapter implements BillerAdapter {

    private static final Logger log = LoggerFactory.getLogger(SimulatedBillerAdapter.class);

    public static final String ADAPTER_CODE = "SIMULATED_BILLER_ADAPTER";

    // Standard simulated billers
    private final List<BillerInfo> billers = List.of(
            new BillerInfo(
                    "SIM_ELEC_01",
                    "Simulated Electricity Board",
                    BillerCategory.ELECTRICITY,
                    "Account Number",
                    "1002345678",
                    "^\\d{8,12}$",
                    new BigDecimal("30.00"),
                    true,
                    true
            ),
            new BillerInfo(
                    "SIM_WATER_01",
                    "Simulated Water Board",
                    BillerCategory.WATER,
                    "Consumer Number",
                    "2003456789",
                    "^\\d{8,12}$",
                    new BigDecimal("25.00"),
                    true,
                    true
            ),
            new BillerInfo(
                    "SIM_MOBI_01",
                    "Simulated Mobile Operator A",
                    BillerCategory.MOBILE,
                    "Mobile Number",
                    "0771234567",
                    "^07\\d{8}$",
                    BigDecimal.ZERO,
                    false,
                    true
            ),
            new BillerInfo(
                    "SIM_INET_01",
                    "Simulated Broadband",
                    BillerCategory.INTERNET,
                    "Broadband Account ID",
                    "BB98765432",
                    "^[A-Za-z0-9]{8,14}$",
                    new BigDecimal("35.00"),
                    true,
                    true
            ),
            new BillerInfo(
                    "SIM_COUNCIL_01",
                    "Simulated Municipal Council",
                    BillerCategory.GOVERNMENT,
                    "Assessment Number",
                    "AS778899",
                    "^[A-Za-z0-9]{6,12}$",
                    new BigDecimal("50.00"),
                    false,
                    true
            )
    );

    // In-memory ledger for simulated biller side to track query / reconciliation state
    private final ConcurrentHashMap<String, BillPaymentResponse> paymentLedger = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> queryCount = new ConcurrentHashMap<>();
    private final Set<String> reversalSet = ConcurrentHashMap.newKeySet();

    @Override
    public String getAdapterCode() {
        return ADAPTER_CODE;
    }

    @Override
    public List<BillerInfo> listBillers() {
        return billers;
    }

    @Override
    public Optional<BillerInfo> getBiller(String billerId) {
        if (billerId == null) return Optional.empty();
        return billers.stream().filter(b -> b.id().equalsIgnoreCase(billerId.trim())).findFirst();
    }

    @Override
    public BillValidationResult validateCustomerReference(String billerId, String customerReference) {
        if (customerReference == null || customerReference.trim().length() < 4) {
            return BillValidationResult.failure("Customer reference is too short or invalid");
        }
        String refUpper = customerReference.trim().toUpperCase();
        if (refUpper.contains("INVALID") || refUpper.contains("ERR")) {
            return BillValidationResult.failure("Customer reference not found with biller");
        }
        return BillValidationResult.success("Simulated Customer (" + customerReference.trim() + ")");
    }

    @Override
    public BillDetails fetchBill(String billerId, String customerReference) {
        BillerInfo biller = getBiller(billerId)
                .orElse(billers.get(0));

        if (customerReference == null || customerReference.trim().isBlank()) {
            return new BillDetails(
                    biller.id(),
                    biller.name(),
                    biller.category(),
                    "",
                    "",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    LocalDate.now().plusDays(14),
                    BillStatus.INVALID_REFERENCE,
                    "Customer reference is required",
                    biller.serviceFee()
            );
        }

        String ref = customerReference.trim();
        String refUpper = ref.toUpperCase();

        // 1. Invalid reference scenario
        if (refUpper.contains("INVALID") || refUpper.contains("ERR") || ref.length() < 4) {
            return new BillDetails(
                    biller.id(),
                    biller.name(),
                    biller.category(),
                    ref,
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null,
                    BillStatus.INVALID_REFERENCE,
                    "Customer reference not recognized by biller",
                    biller.serviceFee()
            );
        }

        // 2. No bill due scenario
        if (refUpper.contains("PAID") || refUpper.contains("ZERO") || ref.endsWith("990")) {
            return new BillDetails(
                    biller.id(),
                    biller.name(),
                    biller.category(),
                    ref,
                    "Simulated Customer",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    LocalDate.now().plusDays(30),
                    BillStatus.NO_BILL_DUE,
                    "No outstanding bill due for this account",
                    biller.serviceFee()
            );
        }

        // 3. Amount changed scenario
        if (refUpper.contains("CHANGED") || refUpper.contains("CHG") || ref.endsWith("991")) {
            return new BillDetails(
                    biller.id(),
                    biller.name(),
                    biller.category(),
                    ref,
                    "Simulated Customer (Updated)",
                    new BigDecimal("3850.00"),
                    new BigDecimal("1000.00"),
                    LocalDate.now().plusDays(7),
                    BillStatus.AMOUNT_CHANGED,
                    "Bill amount was updated by biller recently",
                    biller.serviceFee()
            );
        }

        // 4. Default: Bill found scenario
        BigDecimal amount = new BigDecimal("2500.00");
        return new BillDetails(
                biller.id(),
                biller.name(),
                biller.category(),
                ref,
                "Simulated Customer",
                amount,
                new BigDecimal("500.00"),
                LocalDate.now().plusDays(14),
                BillStatus.BILL_FOUND,
                "Bill details retrieved successfully",
                biller.serviceFee()
        );
    }

    @Override
    public BillPaymentResponse payBill(BillPaymentRequest request) {
        String ref = request.customerReference() != null ? request.customerReference().trim() : "";
        String refUpper = ref.toUpperCase();
        String paymentRef = request.paymentReference();
        String billerConfirmation = "SIM-BILL-" + Math.abs(paymentRef.hashCode() % 1000000);

        log.info("SimulatedBillerAdapter payBill: billerId={}, ref={}, amount={}",
                request.billerId(), ref, request.amount());

        // 1. Hard failure scenario
        if (refUpper.contains("FAIL") || refUpper.contains("REJECT") || ref.endsWith("994")) {
            BillPaymentResponse resp = new BillPaymentResponse(
                    paymentRef,
                    null,
                    TransactionStatus.FAILED,
                    "BILLER_REJECTED: Customer account suspended or closed",
                    request.amount(),
                    Instant.now()
            );
            paymentLedger.put(paymentRef, resp);
            return resp;
        }

        // 2. Biller timeout but paid scenario
        if (refUpper.contains("TIMEOUT") || refUpper.contains("TMO") || ref.endsWith("992")) {
            log.warn("Simulated biller timeout triggered for paymentRef={}", paymentRef);
            // Biller timed out on interface, but processed asynchronously on biller end
            BillPaymentResponse timedOutResp = new BillPaymentResponse(
                    paymentRef,
                    billerConfirmation,
                    TransactionStatus.REQUIRES_RECONCILIATION,
                    "BILLER_TIMEOUT: Biller response timed out, queued for reconciliation",
                    request.amount(),
                    Instant.now()
            );
            // In biller backend, it actually got paid, so queryPayment/reconcile will yield COMPLETED
            paymentLedger.put(paymentRef, new BillPaymentResponse(
                    paymentRef,
                    billerConfirmation,
                    TransactionStatus.COMPLETED,
                    null,
                    request.amount(),
                    Instant.now()
            ));
            return timedOutResp;
        }

        // 3. Delayed confirmation scenario
        if (refUpper.contains("DELAY") || refUpper.contains("PEND") || ref.endsWith("993")) {
            log.info("Simulated delayed confirmation for paymentRef={}", paymentRef);
            BillPaymentResponse pendingResp = new BillPaymentResponse(
                    paymentRef,
                    billerConfirmation,
                    TransactionStatus.PENDING,
                    "BILLER_PENDING: Payment queued at biller",
                    request.amount(),
                    Instant.now()
            );
            paymentLedger.put(paymentRef, new BillPaymentResponse(
                    paymentRef,
                    billerConfirmation,
                    TransactionStatus.COMPLETED,
                    null,
                    request.amount(),
                    Instant.now()
            ));
            return pendingResp;
        }

        // 4. Later reversal scenario
        if (refUpper.contains("REVERSE") || refUpper.contains("REV") || ref.endsWith("995")) {
            reversalSet.add(paymentRef);
            BillPaymentResponse completedResp = new BillPaymentResponse(
                    paymentRef,
                    billerConfirmation,
                    TransactionStatus.COMPLETED,
                    null,
                    request.amount(),
                    Instant.now()
            );
            paymentLedger.put(paymentRef, completedResp);
            return completedResp;
        }

        // 5. Default happy path: COMPLETED
        BillPaymentResponse successResp = new BillPaymentResponse(
                paymentRef,
                billerConfirmation,
                TransactionStatus.COMPLETED,
                null,
                request.amount(),
                Instant.now()
        );
        paymentLedger.put(paymentRef, successResp);
        return successResp;
    }

    @Override
    public BillPaymentResponse queryPayment(String paymentReference) {
        if (paymentReference == null) {
            return new BillPaymentResponse(null, null, TransactionStatus.FAILED, "PAYMENT_NOT_FOUND", BigDecimal.ZERO, Instant.now());
        }

        if (reversalSet.contains(paymentReference)) {
            return new BillPaymentResponse(
                    paymentReference,
                    "SIM-REV-" + Math.abs(paymentReference.hashCode() % 100000),
                    TransactionStatus.REVERSED,
                    "Payment was reversed by biller",
                    BigDecimal.ZERO,
                    Instant.now()
            );
        }

        BillPaymentResponse stored = paymentLedger.get(paymentReference);
        if (stored != null) {
            return stored;
        }

        // Fallback default
        return new BillPaymentResponse(
                paymentReference,
                "SIM-BILL-QUERY-" + Math.abs(paymentReference.hashCode() % 100000),
                TransactionStatus.COMPLETED,
                null,
                BigDecimal.ZERO,
                Instant.now()
        );
    }

    @Override
    public BillerReconcileResult reconcile(String billerId, LocalDate settlementDate) {
        List<BillerReconcileResult.BillerReconcileItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<String, BillPaymentResponse> entry : paymentLedger.entrySet()) {
            BillPaymentResponse r = entry.getValue();
            TransactionStatus finalStatus = reversalSet.contains(r.paymentReference()) ? TransactionStatus.REVERSED : r.status();
            items.add(new BillerReconcileResult.BillerReconcileItem(
                    r.paymentReference(),
                    r.billerPaymentRef(),
                    r.amountPaid(),
                    finalStatus
            ));
            if (finalStatus == TransactionStatus.COMPLETED) {
                total = total.add(r.amountPaid() != null ? r.amountPaid() : BigDecimal.ZERO);
            }
        }

        return new BillerReconcileResult(
                billerId != null ? billerId : "ALL",
                settlementDate != null ? settlementDate : LocalDate.now(),
                items.size(),
                total,
                items
        );
    }

    /**
     * Mark a payment as reversed for tests.
     */
    public void markReversed(String paymentReference) {
        if (paymentReference != null) {
            reversalSet.add(paymentReference);
        }
    }

    /**
     * Clear test ledger.
     */
    public void reset() {
        paymentLedger.clear();
        queryCount.clear();
        reversalSet.clear();
    }
}
