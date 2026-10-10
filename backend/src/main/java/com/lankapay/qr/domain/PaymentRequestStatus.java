package com.lankapay.qr.domain;

/**
 * Lifecycle states of a payment request.
 */
public enum PaymentRequestStatus {
    PENDING,
    COMPLETED,
    EXPIRED,
    CANCELLED
}
