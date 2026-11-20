package com.lankapay.virtualcard.provider;

public enum CardAuthStatus {
    APPROVED,
    DECLINED,
    TIMEOUT,
    REVERSED,
    PENDING_CHALLENGE   // waiting for OTP confirmation
}
