package com.lankapay.notification.domain;

import com.lankapay.identity.domain.User;
import java.time.Instant;

public class NotificationPreference {

    private Long id;

    private User user;

    private boolean inAppEnabled = true;

    private boolean pushEnabled = true;

    private boolean emailEnabled = true;

    private boolean smsEnabled = true;

    private boolean paymentAlerts = true;

    private boolean securityAlerts = true;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    public NotificationPreference() {}

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
