package com.lankapay.bank.adapter.simulated;

import com.lankapay.bank.adapter.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class shared by SimulatedBankAdapterA, B, and C.
 *
 * Implements all 8 BankAdapter operations deterministically using
 * {@link SimulatedAccountStore} and {@link TransferScenario}.
 *
 * Subclasses only need to supply their {@code institutionCode}.
 *
 * ⚠ SIMULATED ONLY — every response includes {@code simulated=true}.
 */
public abstract class AbstractSimulatedBankAdapter implements BankAdapter {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final SimulatedAccountStore store;

    /**
     * Pending transfer state: operationReference → initial scenario.
     * Supports queryTransferStatus / handleCallback for TIMEOUT and DELAYED scenarios.
     */
    private final Map<String, TransferScenario> pendingTransfers = new ConcurrentHashMap<>();

    protected AbstractSimulatedBankAdapter(SimulatedAccountStore store) {
        this.store = store;
    }

    // ── 1. verifyOwnership ─────────────────────────────────────

    @Override
    public AdapterResponse verifyOwnership(AccountOwnershipRequest request) {
        log.info("[SIMULATED][{}] verifyOwnership account=****{}",
                getInstitutionCode(), last4(request.accountNumber()));

        return store.find(getInstitutionCode(), request.accountNumber())
                .map(acct -> {
                    boolean nameMatch = acct.holderName()
                            .equalsIgnoreCase(request.accountHolderName().trim());
                    if (!nameMatch) {
                        return AdapterResponse.failure("ACCOUNT_NAME_MISMATCH",
                                "Account holder name does not match bank records",
                                "OWNERSHIP_CHECK");
                    }
                    if (!acct.active()) {
                        return AdapterResponse.failure("ACCOUNT_INACTIVE",
                                "Account is not active", "OWNERSHIP_CHECK");
                    }
                    return AdapterResponse.success(simRef(request.operationReference()),
                            "OWNERSHIP_VERIFIED");
                })
                .orElseGet(() -> AdapterResponse.failure("ACCOUNT_NOT_FOUND",
                        "Account not found at " + getInstitutionCode(),
                        "OWNERSHIP_CHECK"));
    }

    // ── 2. resolveAccountReference ─────────────────────────────

    @Override
    public AdapterResponse resolveAccountReference(AccountReferenceRequest request) {
        log.info("[SIMULATED][{}] resolveAccountReference identifier={}",
                getInstitutionCode(), request.identifier());

        // In the simulated adapter, identifiers are account numbers directly
        return store.find(getInstitutionCode(), request.identifier())
                .map(acct -> AdapterResponse.success(
                        simRef(request.operationReference()), "REFERENCE_RESOLVED"))
                .orElseGet(() -> AdapterResponse.failure("IDENTIFIER_NOT_FOUND",
                        "Identifier could not be resolved to an account",
                        "REFERENCE_RESOLVE"));
    }

    // ── 3. checkTransferEligibility ────────────────────────────

    @Override
    public AdapterResponse checkTransferEligibility(TransferEligibilityRequest request) {
        log.info("[SIMULATED][{}] checkEligibility masked={} amount={}",
                getInstitutionCode(), request.maskedAccountNumber(), request.amount());

        // Find by masked number suffix
        return store.allForInstitution(getInstitutionCode()).stream()
                .filter(a -> a.maskedNumber().equals(request.maskedAccountNumber()))
                .findFirst()
                .map(acct -> {
                    if (!acct.active()) {
                        return AdapterResponse.failure("ACCOUNT_INACTIVE",
                                "Account is not active", "ELIGIBILITY_CHECK");
                    }
                    if (acct.balance().compareTo(request.amount()) < 0) {
                        return AdapterResponse.failure("INSUFFICIENT_FUNDS",
                                "Account balance is below the requested transfer amount",
                                TransferScenario.INSUFFICIENT_FUNDS.name());
                    }
                    return AdapterResponse.success(
                            simRef(request.operationReference()), "ELIGIBLE");
                })
                .orElseGet(() -> AdapterResponse.success(
                        simRef(request.operationReference()), "ELIGIBLE_ASSUMED"));
    }

    // ── 4. getQuote ────────────────────────────────────────────

    @Override
    public AdapterResponse getQuote(QuoteRequest request) {
        log.info("[SIMULATED][{}] getQuote amount={} {}",
                getInstitutionCode(), request.amount(), request.currency());
        // Simulated: zero fee, LKR only, 5-minute validity
        return AdapterResponse.success(simRef(request.operationReference()), "QUOTE_ZERO_FEE");
    }

    // ── 5. initiateTransfer ────────────────────────────────────

    @Override
    public AdapterResponse initiateTransfer(TransferRequest request) {
        log.info("[SIMULATED][{}] initiateTransfer debit={} credit={} amount={}",
                getInstitutionCode(),
                request.maskedDebitAccount(),
                request.maskedCreditAccount(),
                request.amount());

        // Resolve account by masked number suffix to get scenario override
        TransferScenario accountOverride = store.allForInstitution(getInstitutionCode())
                .stream()
                .filter(a -> a.maskedNumber().equals(request.maskedDebitAccount()))
                .findFirst()
                .map(SimulatedTestAccount::scenarioOverride)
                .orElse(null);

        TransferScenario scenario = TransferScenario.resolve(request.amount(), accountOverride);
        String ref = simRef(request.operationReference());

        return switch (scenario) {
            case SUCCESS -> AdapterResponse.success(ref, scenario.name());

            case INSUFFICIENT_FUNDS -> AdapterResponse.failure(
                    "INSUFFICIENT_FUNDS",
                    "Account does not have sufficient funds [SIMULATED]",
                    scenario.name());

            case TIMEOUT_SUCCEEDED -> {
                // Return timeout but register the transfer as actually completed
                pendingTransfers.put(request.operationReference(),
                        TransferScenario.TIMEOUT_SUCCEEDED);
                yield AdapterResponse.timeout(ref, scenario.name());
            }

            case DELAYED_CONFIRMATION -> {
                pendingTransfers.put(request.operationReference(),
                        TransferScenario.DELAYED_CONFIRMATION);
                yield AdapterResponse.pending(ref, scenario.name());
            }

            case HARD_FAILURE -> AdapterResponse.failure(
                    "BANK_REJECTED",
                    "Transaction rejected by bank — will not retry [SIMULATED]",
                    scenario.name());

            case LATER_REVERSAL -> {
                // Succeeds now; reversal is returned on the next status query
                pendingTransfers.put(request.operationReference(),
                        TransferScenario.LATER_REVERSAL);
                yield AdapterResponse.success(ref, scenario.name());
            }
        };
    }

    // ── 6. queryTransferStatus ─────────────────────────────────

    @Override
    public AdapterResponse queryTransferStatus(TransferStatusRequest request) {
        log.info("[SIMULATED][{}] queryTransferStatus ref={}",
                getInstitutionCode(), request.providerReference());

        TransferScenario pending = pendingTransfers.remove(request.operationReference());
        String ref = simRef(request.operationReference());

        if (pending == null) {
            // No pending record — treat as already completed
            return AdapterResponse.success(ref, "STATUS_ALREADY_FINAL");
        }

        return switch (pending) {
            case TIMEOUT_SUCCEEDED ->
                    // Was reported as timeout; bank actually committed it
                    AdapterResponse.success(ref, pending.name());

            case DELAYED_CONFIRMATION ->
                    AdapterResponse.success(ref, pending.name());

            case LATER_REVERSAL ->
                    AdapterResponse.reversed(ref, pending.name());

            default ->
                    AdapterResponse.success(ref, "STATUS_RESOLVED");
        };
    }

    // ── 7. handleCallback ──────────────────────────────────────

    @Override
    public AdapterResponse handleCallback(CallbackPayload payload) {
        log.info("[SIMULATED][{}] handleCallback ref={}",
                getInstitutionCode(), payload.operationReference());
        // Simulated: delegate to queryTransferStatus with same reference
        return queryTransferStatus(new TransferStatusRequest(
                getInstitutionCode(),
                payload.operationReference(),
                payload.operationReference()));
    }

    // ── 8. reconcile ───────────────────────────────────────────

    @Override
    public AdapterResponse reconcile(ReconcileRequest request) {
        log.info("[SIMULATED][{}] reconcile date={}",
                getInstitutionCode(), request.settlementDate());
        return AdapterResponse.success(
                simRef(request.operationReference()), "RECONCILE_COMPLETE");
    }

    // ── helpers ────────────────────────────────────────────────

    private String simRef(String operationRef) {
        return "SIM-%s-%d".formatted(
                getInstitutionCode().replace("SIMULATED_BANK_", ""),
                System.currentTimeMillis());
    }

    private static String last4(String s) {
        if (s == null || s.length() < 4) return s;
        return s.substring(s.length() - 4);
    }
}
