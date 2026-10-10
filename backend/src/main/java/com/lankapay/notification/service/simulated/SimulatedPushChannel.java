package com.lankapay.notification.service.simulated;

import com.lankapay.notification.service.PushChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SIMULATED push notification channel.
 *
 * ⚠ UNIVERSITY PROTOTYPE – no real push notification is sent.
 * Notifications are written to the application log only.
 */
@Component
public class SimulatedPushChannel implements PushChannel {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPushChannel.class);

    @Override
    public void send(Long userId, String title, String body) {
        log.info("[SIMULATED PUSH] userId={} title='{}' body='{}'", userId, title, body);
    }
}
