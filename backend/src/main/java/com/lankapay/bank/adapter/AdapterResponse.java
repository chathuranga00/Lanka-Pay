package com.lankapay.bank.adapter;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Unified response returned by every BankAdapter operation.
 *
 * Rules:
 * - {@code simulated} is ALWAYS {@code true} in this prototype.
 * - {@code providerReference} is the bank-side transaction ID (or SIM-xxx in tests).
 * - {@code scenario} describes the deterministic test scenario that fired (simulated only).
 * - Stack traces and internal errors NEVER appear in this object.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdapterResponse(

        /** True when the operation completed successfully. */
        boolean success,

        /** Bank-side transaction reference, e.g. "SIM-A-1700000000000-1234". */
        String providerReference,

        /** Machine-readable failure code, null on success. */
        String errorCode,

        /** Human-readable failure reason, null on success. */
        String errorMessage,

        /**
         * Transfer status as reported by the bank.
         * One of: COMPLETED, PENDING, FAILED, REVERSED, TIMEOUT.
         */
        String transferStatus,

        /**
         * Deterministic scenario that was triggered (simulated adapters only).
         * Null for non-simulated adapters.
         */
        String scenario,

        /** Always true in this prototype. */
        boolean simulated,

        /** When the response was produced. */
        Instant respondedAt

) {
    // ── Factory methods ────────────────────────────────────────

    public static AdapterResponse success(String providerRef, String scenario) {
        return new AdapterResponse(true, providerRef, null, null,
                "COMPLETED", scenario, true, Instant.now());
    }

    public static AdapterResponse pending(String providerRef, String scenario) {
        return new AdapterResponse(true, providerRef, null, null,
                "PENDING", scenario, true, Instant.now());
    }

    public static AdapterResponse failure(String errorCode, String errorMessage, String scenario) {
        return new AdapterResponse(false, null, errorCode, errorMessage,
                "FAILED", scenario, true, Instant.now());
    }

    public static AdapterResponse timeout(String providerRef, String scenario) {
        return new AdapterResponse(false, providerRef, "TIMEOUT",
                "Bank did not respond within the timeout window",
                "TIMEOUT", scenario, true, Instant.now());
    }

    public static AdapterResponse reversed(String providerRef, String scenario) {
        return new AdapterResponse(false, providerRef, "REVERSED",
                "Transfer was reversed by the bank",
                "REVERSED", scenario, true, Instant.now());
    }
}
