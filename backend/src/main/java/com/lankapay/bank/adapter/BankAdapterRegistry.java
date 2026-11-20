package com.lankapay.bank.adapter;

import com.lankapay.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes bank operations to the correct {@link BankAdapter} by institution code.
 *
 * All registered {@link BankAdapter} beans are injected via the constructor.
 * Adding a new adapter requires only implementing the interface and registering
 * it as a Spring bean — no changes to this class are needed.
 *
 * Usage:
 * <pre>
 *   BankAdapter adapter = registry.get("SIMULATED_BANK_A");
 *   AdapterResponse resp = adapter.initiateTransfer(request);
 * </pre>
 */
@Component
public class BankAdapterRegistry {

    private static final Logger log = LoggerFactory.getLogger(BankAdapterRegistry.class);

    private final Map<String, BankAdapter> adapters;

    public BankAdapterRegistry(List<BankAdapter> adapterList) {
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(
                        BankAdapter::getInstitutionCode,
                        Function.identity()));

        log.info("BankAdapterRegistry initialised with {} adapter(s): {}",
                adapters.size(), adapters.keySet());
    }

    /**
     * Return the adapter for the given institution code.
     *
     * @param institutionCode e.g. "SIMULATED_BANK_A"
     * @throws ResourceNotFoundException if no adapter is registered for that code
     */
    public BankAdapter get(String institutionCode) {
        BankAdapter adapter = adapters.get(institutionCode);
        if (adapter == null) {
            throw new ResourceNotFoundException("BANK_ADAPTER_NOT_FOUND",
                    "No bank adapter registered for institution code: " + institutionCode
                    + ". Check that the institution is supported and app.bank-adapter.mode is set correctly.");
        }
        return adapter;
    }

    /** Returns true if an adapter is registered for the given code. */
    public boolean supports(String institutionCode) {
        return adapters.containsKey(institutionCode);
    }

    /** All registered institution codes. */
    public java.util.Set<String> registeredCodes() {
        return java.util.Collections.unmodifiableSet(adapters.keySet());
    }
}
