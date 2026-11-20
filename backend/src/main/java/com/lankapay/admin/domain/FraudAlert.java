package com.lankapay.admin.domain;

import com.lankapay.identity.domain.User;
import com.lankapay.transfer.domain.Transfer;
import java.time.Instant;

public class FraudAlert {

    private Long id;

    private User user;

    private Transfer transfer;

    private FraudAlertType alertType;

    private FraudAlertSeverity severity = FraudAlertSeverity.MEDIUM;

    private FraudAlertStatus status = FraudAlertStatus.OPEN;

    private String description;

    private String metadataJson;

    private Instant createdAt = Instant.now();

    private Instant resolvedAt;

    private Long resolvedBy;

    private String resolutionNotes;

    public FraudAlert() {}

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
    public void setId(Long id) { this.id = id; }
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
