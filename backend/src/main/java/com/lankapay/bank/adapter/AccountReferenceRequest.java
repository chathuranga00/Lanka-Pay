package com.lankapay.bank.adapter;

/**
 * Request to resolve a payment identifier (phone number, alias, NIC)
 * to a bank account reference at the given institution.
 * Used before displaying a payee to the sender.
 */
public record AccountReferenceRequest(
        String institutionCode,
        String identifier,             // e.g. phone in E.164, alias string
        String identifierType,         // "PHONE" | "ALIAS" | "NIC"
        String operationReference
) {}
