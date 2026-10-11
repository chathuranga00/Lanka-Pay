package com.lankapay.bank.domain;

import java.time.Instant;

/**
 * Pure domain model representing a linked bank account.
 * Contains no JPA or database-specific annotations.
 */
public record LinkedAccount(
        Long id,
        Long userId,
        String bankCode,
        String accountNumberHash,
        String maskedNumber,
        String accountName,
        String label,
        boolean discoverable,
        boolean receivingEnabled,
        String encryptedAccountReference,
        BankAccountStatus status,
        boolean isDefault,
        Instant linkedAt,
        Instant updatedAt
) {
    public LinkedAccount(Long userId, String bankCode, String accountNumberHash,
                         String maskedNumber, String accountName,
                         String encryptedAccountReference) {
        this(null, userId, bankCode, accountNumberHash, maskedNumber, accountName,
                null, true, true, encryptedAccountReference, BankAccountStatus.ACTIVE,
                false, Instant.now(), Instant.now());
    }

    public boolean canReceive() {
        return status == BankAccountStatus.ACTIVE && receivingEnabled;
    }

    public boolean isActive() {
        return status == BankAccountStatus.ACTIVE;
    }

    public LinkedAccount withDefault(boolean newDefault) {
        return new LinkedAccount(id, userId, bankCode, accountNumberHash, maskedNumber, accountName,
                label, discoverable, receivingEnabled, encryptedAccountReference, status, newDefault, linkedAt, Instant.now());
    }

    public LinkedAccount withStatus(BankAccountStatus newStatus) {
        return new LinkedAccount(id, userId, bankCode, accountNumberHash, maskedNumber, accountName,
                label, discoverable, receivingEnabled, encryptedAccountReference, newStatus, isDefault, linkedAt, Instant.now());
    }

    public LinkedAccount withLabel(String newLabel) {
        return new LinkedAccount(id, userId, bankCode, accountNumberHash, maskedNumber, accountName,
                newLabel, discoverable, receivingEnabled, encryptedAccountReference, status, isDefault, linkedAt, Instant.now());
    }

    public LinkedAccount withDiscoverable(boolean newDiscoverable) {
        return new LinkedAccount(id, userId, bankCode, accountNumberHash, maskedNumber, accountName,
                label, newDiscoverable, receivingEnabled, encryptedAccountReference, status, isDefault, linkedAt, Instant.now());
    }

    public LinkedAccount withReceivingEnabled(boolean newReceivingEnabled) {
        return new LinkedAccount(id, userId, bankCode, accountNumberHash, maskedNumber, accountName,
                label, discoverable, newReceivingEnabled, encryptedAccountReference, status, isDefault, linkedAt, Instant.now());
    }

    public static LinkedAccount fromBankAccount(BankAccount ba) {
        if (ba == null) return null;
        return new LinkedAccount(
                ba.getId(),
                ba.getUser() != null ? ba.getUser().getId() : null,
                ba.getBankCode(),
                ba.getAccountNumberHash(),
                ba.getMaskedNumber(),
                ba.getAccountName(),
                ba.getLabel(),
                ba.isDiscoverable(),
                ba.isReceivingEnabled(),
                ba.getEncryptedAccountReference(),
                ba.getStatus(),
                ba.isDefault(),
                ba.getLinkedAt() != null ? ba.getLinkedAt() : Instant.now(),
                ba.getUpdatedAt() != null ? ba.getUpdatedAt() : Instant.now()
        );
    }
}
