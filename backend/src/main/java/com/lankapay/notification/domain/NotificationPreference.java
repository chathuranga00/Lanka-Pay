package com.lankapay.notification.domain;

import com.lankapay.identity.domain.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "ntf_notification_preferences")
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled = true;

    @Column(name = "payment_alerts", nullable = false)
    private boolean paymentAlerts = true;

    @Column(name = "security_alerts", nullable = false)
    private boolean securityAlerts = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreference() {}

    public NotificationPreference(User user) {
        this.user = user;
        this.inAppEnabled = true;
        this.pushEnabled = true;
        this.emailEnabled = true;
        this.smsEnabled = true;
        this.paymentAlerts = true;
        this.securityAlerts = true;
    }

    public Long getId()                      { return id; }
    public User getUser()                    { return user; }
    public boolean isInAppEnabled()          { return inAppEnabled; }
    public boolean isPushEnabled()           { return pushEnabled; }
    public boolean isEmailEnabled()          { return emailEnabled; }
    public boolean isSmsEnabled()            { return smsEnabled; }
    public boolean isPaymentAlerts()         { return paymentAlerts; }
    public boolean isSecurityAlerts()        { return securityAlerts; }
    public Instant getCreatedAt()            { return createdAt; }
    public Instant getUpdatedAt()            { return updatedAt; }

    public void update(Boolean inAppEnabled, Boolean pushEnabled, Boolean emailEnabled,
                       Boolean smsEnabled, Boolean paymentAlerts, Boolean securityAlerts) {
        if (inAppEnabled != null)    this.inAppEnabled = inAppEnabled;
        if (pushEnabled != null)     this.pushEnabled = pushEnabled;
        if (emailEnabled != null)    this.emailEnabled = emailEnabled;
        if (smsEnabled != null)      this.smsEnabled = smsEnabled;
        if (paymentAlerts != null)   this.paymentAlerts = paymentAlerts;
        if (securityAlerts != null)  this.securityAlerts = securityAlerts;
    }
}
