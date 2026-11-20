package com.lankapay.notification.domain;

import com.lankapay.identity.domain.User;
import java.time.Instant;

/**
 * Domain entity for ntf_notifications.
 * Retained for 90 days.
 * simulated is always true in this prototype.
 */
public class Notification {

    private Long id;

    private User user;

    private NotificationChannelType channel;

    private NotificationType type;

    private String title;

    private NotificationStatus status = NotificationStatus.PENDING;

    /** Message body – must NOT contain amounts, account digits, OTP values, or passwords. */
    private String message;

    /** Always true in this prototype. */
    private boolean simulated = true;

    private boolean read = false;

    private Instant readAt;

    private int retryCount = 0;

    private String failureReason;

    private Instant sentAt;

    private Instant createdAt = Instant.now();

    public Notification() {}

    public Notification(User user, NotificationChannelType channel,
                        NotificationType type, String message) {
        this(user, channel, type, null, message);
    }

    public Notification(User user, NotificationChannelType channel,
                        NotificationType type, String title, String message) {
        this.user = user;
        this.channel = channel;
        this.type = type;
        this.title = title;
        this.message = message;
        this.status = NotificationStatus.PENDING;
        this.read = false;
        this.retryCount = 0;
    }

    public Long getId()                          { return id; }
    public User getUser()                        { return user; }
    public NotificationChannelType getChannel()  { return channel; }
    public NotificationType getType()            { return type; }
    public String getTitle()                     { return title; }
    public NotificationStatus getStatus()        { return status; }
    public String getMessage()                   { return message; }
    public boolean isSimulated()                 { return simulated; }
    public boolean isRead()                      { return read; }
    public Instant getReadAt()                   { return readAt; }
    public int getRetryCount()                   { return retryCount; }
    public String getFailureReason()             { return failureReason; }
    public Instant getSentAt()                   { return sentAt; }
    public Instant getCreatedAt()                { return createdAt; }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
        this.retryCount++;
    }

    public void markFailed(String reason) {
        this.status = NotificationStatus.FAILED;
        this.failureReason = reason != null && reason.length() > 255 ? reason.substring(0, 255) : reason;
        this.retryCount++;
    }

    public void markRead() {
        this.read = true;
        this.readAt = Instant.now();
    }

    public void incrementRetry() {
        this.retryCount++;
    }
}
