package com.lankapay.bill.adapter;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry holding all available BillerAdapter instances.
 */
@Component
public class BillerAdapterRegistry {

    private final Map<String, BillerAdapter> adapters = new ConcurrentHashMap<>();

    public BillerAdapterRegistry(List<BillerAdapter> adapterList) {
        for (BillerAdapter adapter : adapterList) {
            adapters.put(adapter.getAdapterCode().toUpperCase(), adapter);
        }
    }

    public Optional<BillerAdapter> findAdapter(String adapterCode) {
        if (adapterCode == null) return Optional.empty();
        return Optional.ofNullable(adapters.get(adapterCode.toUpperCase()));
    }

    /**
     * Resolves the adapter for a given biller ID. Defaults to SimulatedBillerAdapter.
     */
    public BillerAdapter getAdapterForBiller(String billerId) {
        // In the prototype / simulated environment, SimulatedBillerAdapter handles all billers
        return adapters.values().stream()
                .filter(a -> a.getBiller(billerId).isPresent())
                .findFirst()
                .orElseGet(() -> adapters.getOrDefault(SimulatedBillerAdapter.ADAPTER_CODE, adapters.values().iterator().next()));
    }

    public List<BillerInfo> listAllBillers() {
        return adapters.values().stream()
                .flatMap(a -> a.listBillers().stream())
                .toList();
    }
}
