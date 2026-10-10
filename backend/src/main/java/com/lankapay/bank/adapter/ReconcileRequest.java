package com.lankapay.bank.adapter;

import java.time.LocalDate;

/**
 * Request to reconcile all transactions for a given settlement date.
 * Returns a list of transaction statuses the bank considers final.
 */
public record ReconcileRequest(
        String institutionCode,
        LocalDate settlementDate,
        String operationReference
) {}
