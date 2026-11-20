package com.lankapay.virtualcard.provider;

import java.math.BigDecimal;

/** Authorize a debit against a previously issued card token. */
public record CardAuthRequest(
        String     cardToken,
        Long       cardOwnerUserId,
        BigDecimal amount,
        String     currency,          // always "LKR"
        String     operationReference // UUID idempotency key
) {}
