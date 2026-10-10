package com.lankapay.common.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility that stamps {@code "simulated": true} on every bank adapter
 * and notification response produced in this prototype.
 *
 * IMPORTANT: Every method in this class is only relevant while
 * BANK_ADAPTER_MODE=SIMULATED. This must NEVER be used in production.
 */
public final class SimulatedResponseUtil {

    private SimulatedResponseUtil() {}

    /**
     * Returns a new map containing all entries from {@code base}
     * plus {@code "simulated": true}.
     */
    public static Map<String, Object> stamp(Map<String, Object> base) {
        Map<String, Object> result = new LinkedHashMap<>(base);
        result.put("simulated", true);
        return result;
    }

    /**
     * Generates a fake bank transaction reference with a SIMULATED prefix.
     * Format: SIM-<timestamp>-<random4digits>
     */
    public static String generateSimulatedTxRef() {
        int rand = (int) (Math.random() * 9000) + 1000;
        return "SIM-%d-%d".formatted(System.currentTimeMillis(), rand);
    }
}
