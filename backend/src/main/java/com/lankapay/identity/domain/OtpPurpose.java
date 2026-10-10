package com.lankapay.identity.domain;

public enum OtpPurpose {
    REGISTRATION,
    PHONE_VERIFY,    // standalone phone verification / payment-profile setup
    LOGIN,
    TRANSFER,
    PASSWORD_RESET
}
