package com.lankapay.identity.otp;

import com.lankapay.identity.domain.OtpPurpose;

/**
 * Contract for delivering a one-time password to a user's phone.
 *
 * Implementations:
 *  - {@link TestOtpProvider}  – active when {@code OTP_MODE=TEST} (default).
 *    Logs the OTP to the console only. Never sends a real SMS.
 *  - {@link SmsOtpProvider}   – placeholder for a real SMS gateway.
 *    Active when {@code OTP_MODE=PROD}.
 *
 * Rules for all implementations:
 *  - The plaintext OTP must NEVER be stored or returned to the caller.
 *  - The masked phone (not full E.164) must be used for any logging.
 *  - Every send must include "[SIMULATED]" in the message while
 *    {@code OTP_MODE=TEST} is active.
 */
public interface OtpProvider {

    /**
     * Send the OTP to the user's phone number.
     *
     * @param e164Phone   Full E.164 phone number (used to reach the user; NOT logged)
     * @param maskedPhone Masked phone for logging, e.g. +9477***4567
     * @param plainOtp    The 6-digit plaintext OTP (discarded after this call)
     * @param purpose     Why the OTP is being sent
     * @param expiryMinutes How long (minutes) the OTP is valid, for inclusion in the message
     */
    void send(String e164Phone, String maskedPhone, String plainOtp,
              OtpPurpose purpose, long expiryMinutes);
}
