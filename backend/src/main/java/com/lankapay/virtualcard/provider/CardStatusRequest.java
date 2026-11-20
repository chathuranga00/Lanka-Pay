package com.lankapay.virtualcard.provider;

/** Freeze / unfreeze / terminate a card at the vault. */
public record CardStatusRequest(
        String  cardToken,
        Long    cardOwnerUserId,
        String  targetStatus,       // "FROZEN", "ACTIVE", "TERMINATED"
        String  operationReference
) {}
