package com.lankapay.notification.service;

import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.identity.domain.User;
import com.lankapay.notification.channel.NotificationChannel;
import com.lankapay.notification.domain.*;
import com.lankapay.notification.web.dto.NotificationPreferenceResponse;
import com.lankapay.notification.web.dto.NotificationResponse;
import com.lankapay.notification.web.dto.UpdateNotificationPreferenceRequest;
import com.lankapay.qr.domain.PaymentRequest;
import com.lankapay.transfer.domain.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationStore notificationStore;
    private final NotificationMessageFormatter formatter;
    private final Map<NotificationChannelType, NotificationChannel> channelMap = new EnumMap<>(NotificationChannelType.class);

    public NotificationService(NotificationStore notificationStore,
                               NotificationMessageFormatter formatter,
                               List<NotificationChannel> channels) {
        this.notificationStore = notificationStore;
        this.formatter = formatter;
        if (channels != null) {
            for (NotificationChannel ch : channels) {
                this.channelMap.put(ch.getChannelType(), ch);
            }
        }
    }

    // ── Section 12 Event Notification Triggers ─────────────────

    public void notifyPaymentSent(Transfer transfer) {
        if (transfer == null || transfer.getSender() == null) return;
        String recipientName = transfer.getReceiver() != null ? transfer.getReceiver().getFullName() : "recipient";
        var formatted = formatter.formatPaymentSent(recipientName);
        dispatch(transfer.getSender(), NotificationType.PAYMENT_SENT, formatted.title(), formatted.message());
    }

    public void notifyPaymentReceived(Transfer transfer) {
        if (transfer == null || transfer.getReceiver() == null) return;
        String senderName = transfer.getSender() != null ? transfer.getSender().getFullName() : "sender";
        var formatted = formatter.formatPaymentReceived(senderName);
        dispatch(transfer.getReceiver(), NotificationType.PAYMENT_RECEIVED, formatted.title(), formatted.message());
    }

    public void notifyPaymentFailed(Transfer transfer, String reason) {
        if (transfer == null || transfer.getSender() == null) return;
        var formatted = formatter.formatPaymentFailed();
        dispatch(transfer.getSender(), NotificationType.PAYMENT_FAILED, formatted.title(), formatted.message());
    }

    public void notifyPaymentReversed(Transfer transfer) {
        if (transfer == null) return;
        var formatted = formatter.formatPaymentReversed();
        if (transfer.getSender() != null) {
            dispatch(transfer.getSender(), NotificationType.PAYMENT_REVERSED, formatted.title(), formatted.message());
        }
        if (transfer.getReceiver() != null) {
            dispatch(transfer.getReceiver(), NotificationType.PAYMENT_REVERSED, formatted.title(), formatted.message());
        }
    }

    public void notifyAccountLinked(User user, String bankCode) {
        if (user == null) return;
        var formatted = formatter.formatAccountLinked();
        dispatch(user, NotificationType.ACCOUNT_LINKED, formatted.title(), formatted.message());
    }

    public void notifyAccountRemoved(User user, String bankCode) {
        if (user == null) return;
        var formatted = formatter.formatAccountRemoved();
        dispatch(user, NotificationType.ACCOUNT_REMOVED, formatted.title(), formatted.message());
    }

    public void notifySecurityAlert(User user, String alertReason) {
        if (user == null) return;
        var formatted = formatter.formatSecurityAlert(alertReason);
        dispatch(user, NotificationType.SECURITY_ALERT, formatted.title(), formatted.message());
    }

    public void notifyPaymentRequest(PaymentRequest paymentRequest) {
        if (paymentRequest == null || paymentRequest.getPayer() == null) return;
        String requesterName = paymentRequest.getRequester() != null ? paymentRequest.getRequester().getFullName() : "merchant";
        var formatted = formatter.formatPaymentRequestReceived(requesterName);
        dispatch(paymentRequest.getPayer(), NotificationType.PAYMENT_REQUEST_RECEIVED, formatted.title(), formatted.message());
    }

    // ── Core Safe Dispatch Engine ──────────────────────────────

    /**
     * Dispatches notification across configured channels according to user preferences.
     * Delivery failure is caught, logged, and safely retried without ever throwing to caller.
     */
    public void dispatch(User user, NotificationType type, String title, String message) {
        try {
            // Strictly enforce lock-screen privacy: no amounts, no account digits
            NotificationMessageFormatter.assertPrivacySafe(message);

            NotificationPreferenceRecord pref = notificationStore.findPreferenceByUserId(user.getId())
                    .orElseGet(() -> notificationStore.savePreference(NotificationPreferenceRecord.defaultForUser(user.getId())));

            if (isAlertDisabled(type, pref)) {
                log.info("Notification suppressed by user preference: userId={} type={}", user.getId(), type);
                return;
            }

            if (pref.inAppEnabled()) {
                deliverThroughChannel(NotificationChannelType.IN_APP, user, type, title, message);
            }
            if (pref.pushEnabled()) {
                deliverThroughChannel(NotificationChannelType.PUSH, user, type, title, message);
            }
            if (pref.emailEnabled()) {
                deliverThroughChannel(NotificationChannelType.EMAIL, user, type, title, message);
            }

        } catch (Exception ex) {
            // Delivery failure MUST NEVER break payments or upstream actions!
            log.error("Safe dispatch catch: notification delivery failure for userId={} type={}: {}",
                    user.getId(), type, ex.getMessage(), ex);
        }
    }

    private void deliverThroughChannel(NotificationChannelType channelType,
                                       User user,
                                       NotificationType type,
                                       String title,
                                       String message) {
        Notification notification = new Notification(user, channelType, type, title, message);
        NotificationRecord record = new NotificationRecord(user.getId(), channelType, type, title, message);
        if (notificationStore != null) {
            record = notificationStore.saveNotification(record);
        }

        NotificationChannel channel = channelMap.get(channelType);
        if (channel == null) {
            log.warn("No NotificationChannel registered for type: {}", channelType);
            notification.markFailed("No channel provider registered");
            if (notificationStore != null && record != null) {
                notificationStore.saveNotification(record.markFailed("No channel provider registered"));
            }
            return;
        }

        // Retry loop: max 2 retries (3 total attempts)
        int maxRetries = 2;
        boolean delivered = false;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                channel.deliver(notification, user);
                notification.markSent();
                if (notificationStore != null && record != null) {
                    notificationStore.saveNotification(record.markSent());
                }
                delivered = true;
                break;
            } catch (Exception ex) {
                notification.markFailed(ex.getMessage());
                if (notificationStore != null && record != null) {
                    notificationStore.saveNotification(record.markFailed(ex.getMessage()));
                }
                log.warn("Delivery attempt {} failed on channel {} for userId={}: {}",
                        attempt + 1, channelType, user.getId(), ex.getMessage());
            }
        }

        if (!delivered) {
            log.error("All delivery attempts exhausted for notification channel={} userId={}",
                    channelType, user.getId());
        }
    }

    private boolean isAlertDisabled(NotificationType type, NotificationPreferenceRecord pref) {
        if (type == NotificationType.PAYMENT_SENT ||
            type == NotificationType.PAYMENT_RECEIVED ||
            type == NotificationType.PAYMENT_FAILED ||
            type == NotificationType.PAYMENT_REVERSED ||
            type == NotificationType.PAYMENT_REQUEST_RECEIVED) {
            return !pref.paymentAlerts();
        }
        if (type == NotificationType.SECURITY_ALERT ||
            type == NotificationType.ACCOUNT_FROZEN) {
            return !pref.securityAlerts();
        }
        return false;
    }

    // ── Queries & Preferences API ──────────────────────────────

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(Long userId,
                                                           Boolean unreadOnly,
                                                           NotificationType type,
                                                           Pageable pageable) {
        Page<NotificationRecord> page;
        if (Boolean.TRUE.equals(unreadOnly)) {
            page = notificationStore.findNotificationsByUserIdAndRead(userId, false, pageable);
        } else if (type != null) {
            page = notificationStore.findNotificationsByUserIdAndType(userId, type, pageable);
        } else {
            page = notificationStore.findNotificationsByUserId(userId, pageable);
        }
        return page.map(NotificationResponse::from);
    }

    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long userId) {
        NotificationRecord notif = notificationStore.findNotificationByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("NOTIFICATION_NOT_FOUND",
                        "Notification not found with id: " + notificationId));
        return NotificationResponse.from(notificationStore.saveNotification(notif.markRead(Instant.now())));
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationStore.markAllNotificationsAsReadForUser(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse getPreferences(Long userId) {
        NotificationPreferenceRecord pref = notificationStore.findPreferenceByUserId(userId)
                .orElseGet(() -> notificationStore.savePreference(NotificationPreferenceRecord.defaultForUser(userId)));
        return NotificationPreferenceResponse.from(pref);
    }

    @Transactional
    public NotificationPreferenceResponse updatePreferences(Long userId, UpdateNotificationPreferenceRequest request) {
        NotificationPreferenceRecord pref = notificationStore.findPreferenceByUserId(userId)
                .orElseGet(() -> NotificationPreferenceRecord.defaultForUser(userId));
        NotificationPreferenceRecord updated = pref.update(
                request.inAppEnabled(),
                request.pushEnabled(),
                request.emailEnabled(),
                request.smsEnabled(),
                request.paymentAlerts(),
                request.securityAlerts()
        );
        return NotificationPreferenceResponse.from(notificationStore.savePreference(updated));
    }
}
