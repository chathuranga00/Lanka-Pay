package com.lankapay.notification.domain;

import com.lankapay.identity.domain.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * JPA entity for ntf_notifications.
 * Retained for 90 days; a scheduled job purges older rows.
 * simulated is always true in this prototype.
 */
@Entity
@Table(name = "ntf_notifications",
       indexes = {
           @Index(name = "idx_ntf_notifications_user",   columnList = "user_id, created_at"),
           @Index(name = "idx_ntf_notifications_status", columnList = "status"),
           @Index(name = "idx_ntf_notifications_read",   columnList = "user_id, is_read")
       })
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannelType channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationStatus status = NotificationStatus.PENDING;

    /** Message body – must NOT contain amounts, account digits, OTP values, or passwords. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /** Always true in this prototype. */
    @Column(nullable = false)
    private boolean simulated = true;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "sent_at")
    private Instant sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {}

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
