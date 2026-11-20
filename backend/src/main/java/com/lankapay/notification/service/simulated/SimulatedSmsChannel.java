package com.lankapay.notification.service.simulated;

import com.lankapay.notification.service.SmsChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SIMULATED SMS channel.
 *
 * ⚠ UNIVERSITY PROTOTYPE – no real SMS is sent.
 * Messages are written to the application log only.
 *
 * Active when: OTP_MODE=TEST (default).
 */
@Component
@ConditionalOnProperty(name = "app.otp.mode", havingValue = "TEST", matchIfMissing = true)
public class SimulatedSmsChannel implements SmsChannel {

    private static final Logger log = LoggerFactory.getLogger(SimulatedSmsChannel.class);

    @Override
    public void send(String maskedPhone, String message) {
        // Log masked phone only – full phone number must never appear here
        log.info("[SIMULATED SMS] to={} message={}", maskedPhone, message);
    }
}
