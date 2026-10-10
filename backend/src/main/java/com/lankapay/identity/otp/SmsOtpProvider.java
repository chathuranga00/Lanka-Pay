package com.lankapay.identity.otp;

import com.lankapay.identity.domain.OtpPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * PRODUCTION SMS OTP provider — active when {@code OTP_MODE=PROD}.
 *
 * TODO: Replace the stub body with a real SMS gateway integration
 *       (e.g. Twilio, Dialog, Mobitel) before going live.
 *
 * Rules:
 *  - Inject the gateway client via constructor (not hardcoded).
 *  - Never log the plaintext OTP or the full phone number.
 *  - Implement retries and dead-letter handling for failed deliveries.
 *  - Update Notification row status to SENT or FAILED after each attempt.
 */
@Component
@ConditionalOnProperty(name = "app.otp.mode", havingValue = "PROD")
public class SmsOtpProvider implements OtpProvider {

    private static final Logger log = LoggerFactory.getLogger(SmsOtpProvider.class);

    @Override
    public void send(String e164Phone, String maskedPhone, String plainOtp,
                     OtpPurpose purpose, long expiryMinutes) {

        // ── TODO: implement real SMS gateway call ──────────────
        //
        // Example Twilio call (pseudocode):
        //   twilioClient.messages.create(
        //       MessageCreator(
        //           PhoneNumber(e164Phone),
        //           PhoneNumber(twilioFromNumber),
        //           "Your Lanka Pay OTP: " + plainOtp + ". Valid " + expiryMinutes + " min."
        //       )
        //   );
        //
        // ──────────────────────────────────────────────────────

        log.warn("[SmsOtpProvider] PROD mode active but gateway not implemented. " +
                 "OTP NOT delivered to maskedPhone={}", maskedPhone);

        throw new UnsupportedOperationException(
                "SmsOtpProvider is not yet implemented. " +
                "Configure a real SMS gateway before setting OTP_MODE=PROD.");
    }
}
