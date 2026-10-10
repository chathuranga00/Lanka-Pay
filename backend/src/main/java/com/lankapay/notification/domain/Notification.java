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
           @Index(name = "idx_ntf_notifications_status", columnList = "status")
       })
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationStatus status = NotificationStatus.PENDING;

    /** Message body – must NOT contain OTP values, full account numbers, or passwords. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /** Always true in this prototype. */
    @Column(nullable = false)
    private boolean simulated = true;

    @Column(name = "sent_at")
    private Instant sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {}

    public Notification(User user, NotificationChannel channel,
                        NotificationType type, String message) {
        this.user = user;
        this.channel = channel;
        this.type = type;
        this.message = message;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public NotificationChannel getChannel() { return channel; }
    public NotificationType getType() { return type; }
    public NotificationStatus getStatus() { return status; }
    public String getMessage() { return message; }
    public boolean isSimulated() { return simulated; }
    public Instant getSentAt() { return sentAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
    }
}
