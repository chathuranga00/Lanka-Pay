package com.lankapay.bank.adapter;

import java.util.Map;

/**
 * Raw callback payload received from a bank's webhook/notification endpoint.
 * The adapter is responsible for parsing and validating the signature.
 */
public record CallbackPayload(
        String institutionCode,
        String rawBody,
        Map<String, String> headers,
        String operationReference
) {}
