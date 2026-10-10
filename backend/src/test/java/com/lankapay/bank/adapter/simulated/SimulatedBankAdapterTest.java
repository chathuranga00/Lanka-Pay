package com.lankapay.bank.adapter.simulated;

import com.lankapay.bank.adapter.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for all three simulated bank adapters.
 *
 * Tests verify:
 *  1. All 6 deterministic scenarios via magic amounts (A, B, C)
 *  2. Account-flag scenario overrides (A only — same logic applies to B and C)
 *  3. verifyOwnership (name match / name mismatch / not found)
 *  4. resolveAccountReference
 *  5. checkTransferEligibility
 *  6. queryTransferStatus after timeout / delayed / reversal
 *  7. BankAdapterRegistry routing and unknown-code handling
 *  8. FutureAuthorizedBankAdapter throws NotImplemented
 *
 * No Spring context needed — all beans constructed directly.
 */
class SimulatedBankAdapterTest {

    SimulatedAccountStore store;
    SimulatedBankAdapterA adapterA;
    SimulatedBankAdapterB adapterB;
    SimulatedBankAdapterC adapterC;
    BankAdapterRegistry   registry;

    @BeforeEach
    void setUp() {
        store    = new SimulatedAccountStore();
        adapterA = new SimulatedBankAdapterA(store);
        adapterB = new SimulatedBankAdapterB(store);
        adapterC = new SimulatedBankAdapterC(store);
        registry = new BankAdapterRegistry(java.util.List.of(adapterA, adapterB, adapterC));
    }

    // ── helpers ────────────────────────────────────────────────

    private TransferRequest transferReq(AbstractSimulatedBankAdapter adapter,
                                        String maskedDebit, BigDecimal amount) {
        return new TransferRequest(
                adapter.getInstitutionCode(),
                maskedDebit, "****DEST",
                amount, "LKR", null, uuid());
    }

    private String uuid() { return UUID.randomUUID().toString(); }

    // ── Scenario tests (magic amounts) ────────────────────────

    @Nested @DisplayName("Deterministic scenarios via magic amounts – all 3 adapters")
    class MagicAmountScenarios {

        @ParameterizedTest(name = "[{0}] amount={1} → scenario={2}")
        @CsvSource({
            "A, 1000.00,  SUCCESS",
            "A, 10000.00, INSUFFICIENT_FUNDS",
            "A, 20000.00, TIMEOUT_SUCCEEDED",
            "A, 30000.00, DELAYED_CONFIRMATION",
            "A, 40000.00, HARD_FAILURE",
            "A, 50000.00, LATER_REVERSAL",
            "B, 500.00,   SUCCESS",
            "B, 10000.00, INSUFFICIENT_FUNDS",
            "B, 20000.00, TIMEOUT_SUCCEEDED",
            "B, 30000.00, DELAYED_CONFIRMATION",
            "B, 40000.00, HARD_FAILURE",
            "B, 50000.00, LATER_REVERSAL",
            "C, 9999.99,  SUCCESS",
            "C, 10000.00, INSUFFICIENT_FUNDS",
            "C, 20000.00, TIMEOUT_SUCCEEDED",
            "C, 30000.00, DELAYED_CONFIRMATION",
            "C, 40000.00, HARD_FAILURE",
            "C, 50000.00, LATER_REVERSAL",
        })
        void scenarioByAmount(String bank, String amountStr, String expectedScenario) {
            AbstractSimulatedBankAdapter adapter = switch (bank) {
                case "A" -> adapterA;
                case "B" -> adapterB;
                default  -> adapterC;
            };

            TransferRequest req = transferReq(adapter, "****DEBIT",
                    new BigDecimal(amountStr));
            AdapterResponse resp = adapter.initiateTransfer(req);

            assertThat(resp.scenario()).isEqualTo(expectedScenario);
            assertThat(resp.simulated()).isTrue();

            switch (expectedScenario) {
                case "SUCCESS"              -> assertThat(resp.success()).isTrue();
                case "INSUFFICIENT_FUNDS",
                     "HARD_FAILURE"         -> assertThat(resp.success()).isFalse();
                case "TIMEOUT_SUCCEEDED"    -> {
                    assertThat(resp.success()).isFalse();
                    assertThat(resp.transferStatus()).isEqualTo("TIMEOUT");
                }
                case "DELAYED_CONFIRMATION" -> {
                    assertThat(resp.success()).isTrue();
                    assertThat(resp.transferStatus()).isEqualTo("PENDING");
                }
                case "LATER_REVERSAL"       -> assertThat(resp.success()).isTrue();
            }
        }
    }

    // ── queryTransferStatus after async scenarios ──────────────

    @Nested @DisplayName("queryTransferStatus – async resolution")
    class QueryStatus {

        @Test @DisplayName("TIMEOUT_SUCCEEDED → COMPLETED on poll (Bank A)")
        void timeoutBecomesCompleted() {
            String opRef = uuid();
            TransferRequest req = new TransferRequest(
                    "SIMULATED_BANK_A", "****DEBIT", "****CREDIT",
                    new BigDecimal("20000.00"), "LKR", null, opRef);

            AdapterResponse initResp = adapterA.initiateTransfer(req);
            assertThat(initResp.transferStatus()).isEqualTo("TIMEOUT");

            AdapterResponse statusResp = adapterA.queryTransferStatus(
                    new TransferStatusRequest("SIMULATED_BANK_A", initResp.providerReference(), opRef));

            assertThat(statusResp.success()).isTrue();
            assertThat(statusResp.transferStatus()).isEqualTo("COMPLETED");
            assertThat(statusResp.scenario()).isEqualTo("TIMEOUT_SUCCEEDED");
        }

        @Test @DisplayName("DELAYED_CONFIRMATION → COMPLETED on poll (Bank B)")
        void delayedBecomesCompleted() {
            String opRef = uuid();
            TransferRequest req = new TransferRequest(
                    "SIMULATED_BANK_B", "****DEBIT", "****CREDIT",
                    new BigDecimal("30000.00"), "LKR", null, opRef);

            AdapterResponse initResp = adapterB.initiateTransfer(req);
            assertThat(initResp.transferStatus()).isEqualTo("PENDING");

            AdapterResponse statusResp = adapterB.queryTransferStatus(
                    new TransferStatusRequest("SIMULATED_BANK_B", initResp.providerReference(), opRef));

            assertThat(statusResp.success()).isTrue();
            assertThat(statusResp.transferStatus()).isEqualTo("COMPLETED");
        }

        @Test @DisplayName("LATER_REVERSAL → REVERSED on poll (Bank C)")
        void laterReversalBecomesReversed() {
            String opRef = uuid();
            TransferRequest req = new TransferRequest(
                    "SIMULATED_BANK_C", "****DEBIT", "****CREDIT",
                    new BigDecimal("50000.00"), "LKR", null, opRef);

            AdapterResponse initResp = adapterC.initiateTransfer(req);
            assertThat(initResp.success()).isTrue();      // initially succeeds

            AdapterResponse statusResp = adapterC.queryTransferStatus(
                    new TransferStatusRequest("SIMULATED_BANK_C", initResp.providerReference(), opRef));

            assertThat(statusResp.success()).isFalse();
            assertThat(statusResp.transferStatus()).isEqualTo("REVERSED");
            assertThat(statusResp.scenario()).isEqualTo("LATER_REVERSAL");
        }
    }

    // ── Account-flag scenario overrides ───────────────────────

    @Nested @DisplayName("Account-flag scenario overrides (Bank A)")
    class AccountFlagOverrides {

        @Test @DisplayName("Account 100100 always triggers INSUFFICIENT_FUNDS")
        void accountFlagInsufficient() {
            AdapterResponse resp = adapterA.initiateTransfer(new TransferRequest(
                    "SIMULATED_BANK_A", "****0100", "****DEST",
                    new BigDecimal("1.00"), "LKR", null, uuid()));
            assertThat(resp.scenario()).isEqualTo("INSUFFICIENT_FUNDS");
            assertThat(resp.success()).isFalse();
        }

        @Test @DisplayName("Account 100200 always triggers TIMEOUT_SUCCEEDED")
        void accountFlagTimeout() {
            AdapterResponse resp = adapterA.initiateTransfer(new TransferRequest(
                    "SIMULATED_BANK_A", "****0200", "****DEST",
                    new BigDecimal("1.00"), "LKR", null, uuid()));
            assertThat(resp.scenario()).isEqualTo("TIMEOUT_SUCCEEDED");
            assertThat(resp.transferStatus()).isEqualTo("TIMEOUT");
        }

        @Test @DisplayName("Account 100400 always triggers HARD_FAILURE")
        void accountFlagHardFailure() {
            AdapterResponse resp = adapterA.initiateTransfer(new TransferRequest(
                    "SIMULATED_BANK_A", "****0400", "****DEST",
                    new BigDecimal("1.00"), "LKR", null, uuid()));
            assertThat(resp.scenario()).isEqualTo("HARD_FAILURE");
            assertThat(resp.success()).isFalse();
            assertThat(resp.errorCode()).isEqualTo("BANK_REJECTED");
        }
    }

    // ── verifyOwnership ────────────────────────────────────────

    @Nested @DisplayName("verifyOwnership")
    class VerifyOwnership {

        @Test @DisplayName("success – name matches")
        void nameMatches() {
            AdapterResponse resp = adapterA.verifyOwnership(new AccountOwnershipRequest(
                    "SIMULATED_BANK_A", "101001", "Kamal Perera", uuid()));
            assertThat(resp.success()).isTrue();
            assertThat(resp.simulated()).isTrue();
        }

        @Test @DisplayName("failure – name mismatch")
        void nameMismatch() {
            AdapterResponse resp = adapterA.verifyOwnership(new AccountOwnershipRequest(
                    "SIMULATED_BANK_A", "101001", "Wrong Name", uuid()));
            assertThat(resp.success()).isFalse();
            assertThat(resp.errorCode()).isEqualTo("ACCOUNT_NAME_MISMATCH");
        }

        @Test @DisplayName("failure – account not found")
        void accountNotFound() {
            AdapterResponse resp = adapterA.verifyOwnership(new AccountOwnershipRequest(
                    "SIMULATED_BANK_A", "999999", "Anyone", uuid()));
            assertThat(resp.success()).isFalse();
            assertThat(resp.errorCode()).isEqualTo("ACCOUNT_NOT_FOUND");
        }

        @Test @DisplayName("Bank B account found by holder name")
        void bankBSuccess() {
            AdapterResponse resp = adapterB.verifyOwnership(new AccountOwnershipRequest(
                    "SIMULATED_BANK_B", "201001", "Priya Rajapaksa", uuid()));
            assertThat(resp.success()).isTrue();
        }

        @Test @DisplayName("Bank C account found by holder name")
        void bankCSuccess() {
            AdapterResponse resp = adapterC.verifyOwnership(new AccountOwnershipRequest(
                    "SIMULATED_BANK_C", "301002", "Sewwandi Kumari", uuid()));
            assertThat(resp.success()).isTrue();
        }
    }

    // ── resolveAccountReference ────────────────────────────────

    @Nested @DisplayName("resolveAccountReference")
    class ResolveReference {

        @Test @DisplayName("resolves known account number")
        void found() {
            AdapterResponse resp = adapterA.resolveAccountReference(
                    new AccountReferenceRequest("SIMULATED_BANK_A",
                            "101002", "ACCOUNT_NUMBER", uuid()));
            assertThat(resp.success()).isTrue();
        }

        @Test @DisplayName("returns failure for unknown identifier")
        void notFound() {
            AdapterResponse resp = adapterA.resolveAccountReference(
                    new AccountReferenceRequest("SIMULATED_BANK_A",
                            "UNKNOWN", "ACCOUNT_NUMBER", uuid()));
            assertThat(resp.success()).isFalse();
            assertThat(resp.errorCode()).isEqualTo("IDENTIFIER_NOT_FOUND");
        }
    }

    // ── checkTransferEligibility ───────────────────────────────

    @Nested @DisplayName("checkTransferEligibility")
    class Eligibility {

        @Test @DisplayName("eligible when balance sufficient")
        void eligible() {
            AdapterResponse resp = adapterA.checkTransferEligibility(
                    new TransferEligibilityRequest("SIMULATED_BANK_A",
                            "****1001", new BigDecimal("100.00"), "LKR", uuid()));
            assertThat(resp.success()).isTrue();
        }

        @Test @DisplayName("INSUFFICIENT_FUNDS for zero-balance override account")
        void insufficientFlag() {
            AdapterResponse resp = adapterA.checkTransferEligibility(
                    new TransferEligibilityRequest("SIMULATED_BANK_A",
                            "****0100", new BigDecimal("1.00"), "LKR", uuid()));
            assertThat(resp.success()).isFalse();
            assertThat(resp.errorCode()).isEqualTo("INSUFFICIENT_FUNDS");
        }
    }

    // ── getQuote ───────────────────────────────────────────────

    @Test @DisplayName("getQuote always returns zero-fee success (simulated)")
    void getQuoteZeroFee() {
        AdapterResponse resp = adapterA.getQuote(
                new QuoteRequest("SIMULATED_BANK_A",
                        new BigDecimal("5000.00"), "LKR", uuid()));
        assertThat(resp.success()).isTrue();
        assertThat(resp.scenario()).isEqualTo("QUOTE_ZERO_FEE");
    }

    // ── handleCallback ─────────────────────────────────────────

    @Test @DisplayName("handleCallback delegates to queryTransferStatus")
    void handleCallback() {
        String opRef = uuid();
        // First initiate a timeout scenario to register it as pending
        adapterA.initiateTransfer(new TransferRequest(
                "SIMULATED_BANK_A", "****DEBIT", "****CREDIT",
                new BigDecimal("20000.00"), "LKR", null, opRef));

        AdapterResponse resp = adapterA.handleCallback(new CallbackPayload(
                "SIMULATED_BANK_A", "{}", Map.of(), opRef));

        // Timeout resolves to COMPLETED via callback
        assertThat(resp.success()).isTrue();
        assertThat(resp.transferStatus()).isEqualTo("COMPLETED");
    }

    // ── reconcile ──────────────────────────────────────────────

    @Test @DisplayName("reconcile always succeeds (simulated)")
    void reconcile() {
        AdapterResponse resp = adapterA.reconcile(
                new ReconcileRequest("SIMULATED_BANK_A", LocalDate.now(), uuid()));
        assertThat(resp.success()).isTrue();
        assertThat(resp.scenario()).isEqualTo("RECONCILE_COMPLETE");
    }

    // ── BankAdapterRegistry ────────────────────────────────────

    @Nested @DisplayName("BankAdapterRegistry")
    class Registry {

        @Test @DisplayName("routes to Bank A by institution code")
        void routesA() {
            BankAdapter adapter = registry.get("SIMULATED_BANK_A");
            assertThat(adapter.getInstitutionCode()).isEqualTo("SIMULATED_BANK_A");
        }

        @Test @DisplayName("routes to Bank B by institution code")
        void routesB() {
            assertThat(registry.get("SIMULATED_BANK_B").getInstitutionCode())
                    .isEqualTo("SIMULATED_BANK_B");
        }

        @Test @DisplayName("routes to Bank C by institution code")
        void routesC() {
            assertThat(registry.get("SIMULATED_BANK_C").getInstitutionCode())
                    .isEqualTo("SIMULATED_BANK_C");
        }

        @Test @DisplayName("throws ResourceNotFoundException for unknown institution code")
        void unknownCode() {
            assertThatThrownBy(() -> registry.get("UNKNOWN_BANK"))
                    .isInstanceOf(com.lankapay.common.exception.ResourceNotFoundException.class)
                    .extracting("errorCode").isEqualTo("BANK_ADAPTER_NOT_FOUND");
        }

        @Test @DisplayName("supports() returns true for known codes, false for unknown")
        void supportsCheck() {
            assertThat(registry.supports("SIMULATED_BANK_A")).isTrue();
            assertThat(registry.supports("NONEXISTENT")).isFalse();
        }

        @Test @DisplayName("registeredCodes() contains all 3 simulated banks")
        void registeredCodes() {
            assertThat(registry.registeredCodes())
                    .containsExactlyInAnyOrder(
                            "SIMULATED_BANK_A", "SIMULATED_BANK_B", "SIMULATED_BANK_C");
        }
    }

    // ── FutureAuthorizedBankAdapter ────────────────────────────

    @Nested @DisplayName("FutureAuthorizedBankAdapter")
    class FutureAdapter {

        FutureAuthorizedBankAdapter future = new FutureAuthorizedBankAdapter();

        @Test @DisplayName("getInstitutionCode throws UnsupportedOperationException")
        void getCodeThrows() {
            assertThatThrownBy(future::getInstitutionCode)
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("not yet implemented");
        }

        @Test @DisplayName("initiateTransfer throws UnsupportedOperationException")
        void initiateTransferThrows() {
            assertThatThrownBy(() -> future.initiateTransfer(
                    new TransferRequest("X", "****", "****",
                            BigDecimal.ONE, "LKR", null, uuid())))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("not yet implemented");
        }

        @Test @DisplayName("verifyOwnership throws UnsupportedOperationException")
        void verifyOwnershipThrows() {
            assertThatThrownBy(() -> future.verifyOwnership(
                    new AccountOwnershipRequest("X", "acc", "name", uuid())))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ── TransferScenario resolver ──────────────────────────────

    @Nested @DisplayName("TransferScenario.resolve()")
    class ScenarioResolve {

        @Test @DisplayName("account override wins regardless of amount")
        void overrideWins() {
            assertThat(TransferScenario.resolve(
                    new BigDecimal("1.00"), TransferScenario.HARD_FAILURE))
                    .isEqualTo(TransferScenario.HARD_FAILURE);
        }

        @Test @DisplayName("null override falls back to amount logic")
        void nullOverrideFallsBack() {
            assertThat(TransferScenario.resolve(new BigDecimal("40000.00"), null))
                    .isEqualTo(TransferScenario.HARD_FAILURE);
        }

        @Test @DisplayName("non-magic amount resolves to SUCCESS")
        void nonMagicIsSuccess() {
            assertThat(TransferScenario.resolve(new BigDecimal("123.45"), null))
                    .isEqualTo(TransferScenario.SUCCESS);
        }
    }
}
