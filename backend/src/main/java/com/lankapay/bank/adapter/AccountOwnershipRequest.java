package com.lankapay.bank.adapter;

/**
 * Request to verify that a bank account exists and is owned by the named person.
 * Used during bank-account linking; plaintext account number is hashed by the caller
 * before storage but is needed here for the real (or simulated) bank lookup.
 *
 * Security: accountNumber is only present during the verification call and is
 * never persisted in Lanka Pay's database.
 */
public record AccountOwnershipRequest(
        String institutionCode,
        String accountNumber,          // full number – sent to bank, never stored
        String accountHolderName,
        String operationReference      // UUID idempotency key
) {}
