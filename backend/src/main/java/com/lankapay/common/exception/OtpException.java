package com.lankapay.common.exception;

/**
 * Thrown for all OTP-related failures.
 * errorCode distinguishes the specific case:
 *   OTP_INVALID      – wrong code (HTTP 400)
 *   OTP_EXPIRED      – TTL elapsed (HTTP 410)
 *   OTP_MAX_ATTEMPTS – too many failures, account locked (HTTP 429)
 */
public class OtpException extends LankaPayException {

    public OtpException(String errorCode, String message) {
        super(errorCode, message);
    }

    public static OtpException invalid() {
        return new OtpException("OTP_INVALID", "The OTP provided is incorrect");
    }

    public static OtpException expired() {
        return new OtpException("OTP_EXPIRED", "The OTP has expired. Please request a new one");
    }

    public static OtpException maxAttemptsReached() {
        return new OtpException("OTP_MAX_ATTEMPTS",
                "Too many failed OTP attempts. Account locked for 15 minutes");
    }
}
