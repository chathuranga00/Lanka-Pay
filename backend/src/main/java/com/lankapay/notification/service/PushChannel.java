package com.lankapay.notification.service;

/**
 * Contract for push notification delivery.
 * The only active implementation in this prototype is SimulatedPushChannel.
 */
public interface PushChannel {

    /**
     * @param userId   Recipient user ID (used to resolve device token internally)
     * @param title    Notification title
     * @param body     Notification body
     */
    void send(Long userId, String title, String body);
}
