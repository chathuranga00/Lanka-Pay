package com.lankapay.notification.service;

/**
 * Contract for SMS delivery.
 * The only active implementation in this prototype is SimulatedSmsChannel.
 * Phone numbers passed here must already be masked by the caller – this interface
 * must never receive a full phone number to prevent accidental logging.
 */
public interface SmsChannel {

    /**
     * @param maskedPhone  Masked phone number for logging only (e.g. +9477***4567)
     * @param message      Message body – must NOT contain OTP plaintext
     */
    void send(String maskedPhone, String message);
}
