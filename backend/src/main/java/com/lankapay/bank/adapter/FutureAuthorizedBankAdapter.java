package com.lankapay.bank.adapter;

/**
 * Placeholder for a future real bank integration.
 *
 * This stub is NOT registered as a Spring bean and is NEVER active by default.
 * It exists to:
 *  1. Document the extension point clearly in source control.
 *  2. Give developers a starting template for a real adapter.
 *  3. Throw a clear {@link UnsupportedOperationException} if accidentally wired.
 *
 * To activate:
 *  - Annotate with {@code @Component}.
 *  - Set {@code @ConditionalOnProperty(name="app.bank-adapter.mode", havingValue="REAL_BANK_X")}.
 *  - Set {@code BANK_ADAPTER_MODE=REAL_BANK_X} in the environment.
 *  - Replace every method body with a real HTTP/SDK call.
 *
 * Institution code placeholder: "FUTURE_BANK_X"
 * Replace with the real institution code before activating.
 */
public class FutureAuthorizedBankAdapter implements BankAdapter {

    private static final String NOT_IMPLEMENTED =
            "FutureAuthorizedBankAdapter is not yet implemented. "
            + "This is a stub for a future real bank integration. "
            + "Set app.bank-adapter.mode=SIMULATED to use the SIMULATED adapters, "
            + "or implement this class before enabling REAL mode.";

    @Override
    public String getInstitutionCode() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public AdapterResponse verifyOwnership(AccountOwnershipRequest request) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public AdapterResponse resolveAccountReference(AccountReferenceRequest request) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public AdapterResponse checkTransferEligibility(TransferEligibilityRequest request) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public AdapterResponse getQuote(QuoteRequest request) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public AdapterResponse initiateTransfer(TransferRequest request) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public AdapterResponse queryTransferStatus(TransferStatusRequest request) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public AdapterResponse handleCallback(CallbackPayload payload) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public AdapterResponse reconcile(ReconcileRequest request) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }
}
