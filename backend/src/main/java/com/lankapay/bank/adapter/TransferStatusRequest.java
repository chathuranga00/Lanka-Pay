package com.lankapay.bank.adapter;

/**
 * Request to query the current status of a previously initiated transfer.
 * Used for polling after a timeout-but-actually-succeeded scenario.
 */
public record TransferStatusRequest(
        String institutionCode,
        String providerReference,      // reference returned by initiateTransfer
        String operationReference
) {}
