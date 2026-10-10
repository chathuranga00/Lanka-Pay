package com.lankapay.identity.otp;

import com.lankapay.identity.domain.OtpPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * TEST OTP provider — active when {@code OTP_MODE=TEST} (the default).
 *
 * ⚠ UNIVERSITY PROTOTYPE — no real SMS is ever sent by this provider.
 *
 * Behaviour:
 *  - Logs the plaintext OTP to the application console so developers can
 *    complete the verification flow without real SMS infrastructure.
 *  - Every log line is prefixed with [TEST OTP] so it is impossible to
 *    confuse test output with a real delivery receipt.
 *  - The full phone number is NEVER logged — only the masked form.
 *
 * This bean must NEVER be active in a production environment.
 * Switch to the real provider by setting {@code OTP_MODE=PROD}.
 */
@Component
@ConditionalOnProperty(name = "app.otp.mode", havingValue = "TEST", matchIfMissing = true)
public class TestOtpProvider implements OtpProvider {

    private static final Logger log = LoggerFactory.getLogger(TestOtpProvider.class);

    @Override
    public void send(String e164Phone, String maskedPhone, String plainOtp,
                     OtpPurpose purpose, long expiryMinutes) {

        // e164Phone intentionally NOT logged — only maskedPhone is safe to log
        log.info("""

                ╔══════════════════════════════════════════════════════╗
                ║  [TEST OTP] — SIMULATED — NO REAL SMS SENT           ║
                ║  To      : {}
                ║  Purpose : {}
                ║  OTP     : {}
                ║  Valid   : {} minute(s)
                ╚══════════════════════════════════════════════════════╝
                """,
                maskedPhone, purpose, plainOtp, expiryMinutes);
    }
}
