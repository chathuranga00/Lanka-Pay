package com.lankapay.common.exception;

/**
 * Thrown when a business rule prevents processing (HTTP 422).
 * Examples: DAILY_LIMIT_EXCEEDED, BANK_DEBIT_FAILED, INSUFFICIENT_FUNDS.
 */
public class UnprocessableEntityException extends LankaPayException {

    public UnprocessableEntityException(String errorCode, String message) {
        super(errorCode, message);
    }

    public UnprocessableEntityException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
