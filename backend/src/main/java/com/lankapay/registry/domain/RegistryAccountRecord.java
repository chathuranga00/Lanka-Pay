package com.lankapay.registry.domain;

/**
 * Pure domain representation of an account view inside the payment registry
 * ("paymentRegistry/{phone}/accounts/{accountId}").
 *
 * Holds discovery and receiving consent flags and a denormalized safe view
 * (bank name + masked number) without sensitive or raw account references.
 */
public record RegistryAccountRecord(
        Long accountId,
        String bankCode,
        String bankName,
        String maskedNumber,
        boolean discoverable,
        boolean receivingEnabled,
        boolean isDefault,
        String status
) {
    public boolean canReceive() {
        return "ACTIVE".equalsIgnoreCase(status) && receivingEnabled;
    }

    public boolean isEligible() {
        return canReceive() && discoverable;
    }
}
