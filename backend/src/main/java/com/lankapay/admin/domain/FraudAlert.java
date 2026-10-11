package com.lankapay.admin.domain;

import com.lankapay.identity.domain.User;
import com.lankapay.transfer.domain.Transfer;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "rsk_fraud_alerts",
       indexes = {
           @Index(name = "idx_rsk_fraud_alerts_user", columnList = "user_id, created_at"),
           @Index(name = "idx_rsk_fraud_alerts_status", columnList = "status, severity")
       })
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id")
    private Transfer transfer;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 50)
    private FraudAlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FraudAlertSeverity severity = FraudAlertSeverity.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FraudAlertStatus status = FraudAlertStatus.OPEN;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolution_notes", length = 500)
    private String resolutionNotes;

    protected FraudAlert() {}

    public FraudAlert(User user, Transfer transfer, FraudAlertType alertType,
                      FraudAlertSeverity severity, String description, String metadataJson) {
        this.user = user;
        this.transfer = transfer;
        this.alertType = alertType;
        this.severity = severity;
        this.description = description;
        this.metadataJson = metadataJson;
        this.status = FraudAlertStatus.OPEN;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Transfer getTransfer() { return transfer; }
    public FraudAlertType getAlertType() { return alertType; }
    public FraudAlertSeverity getSeverity() { return severity; }
    public FraudAlertStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public String getMetadataJson() { return metadataJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Long getResolvedBy() { return resolvedBy; }
    public String getResolutionNotes() { return resolutionNotes; }

    public void updateStatus(FraudAlertStatus newStatus, Long adminId, String notes) {
        this.status = newStatus;
        this.resolvedBy = adminId;
        this.resolutionNotes = notes;
        if (newStatus == FraudAlertStatus.RESOLVED || newStatus == FraudAlertStatus.DISMISSED) {
            this.resolvedAt = Instant.now();
        }
    }
}
