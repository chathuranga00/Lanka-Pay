package com.lankapay.admin.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "rsk_risk_rules")
public class RiskRules {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "max_transaction_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal maxTransactionAmount = new BigDecimal("50000.00");

    @Column(name = "max_daily_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal maxDailyAmount = new BigDecimal("100000.00");

    @Column(name = "max_velocity_count_per_hour", nullable = false)
    private int maxVelocityCountPerHour = 5;

    @Column(name = "new_account_age_hours", nullable = false)
    private int newAccountAgeHours = 24;

    @Column(name = "new_account_max_transaction_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal newAccountMaxTransactionAmount = new BigDecimal("10000.00");

    @Column(name = "new_account_max_daily_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal newAccountMaxDailyAmount = new BigDecimal("25000.00");

    @Column(name = "velocity_window_minutes", nullable = false)
    private int velocityWindowMinutes = 60;

    @Column(nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    public RiskRules() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BigDecimal getMaxTransactionAmount() { return maxTransactionAmount; }
    public void setMaxTransactionAmount(BigDecimal maxTransactionAmount) { this.maxTransactionAmount = maxTransactionAmount; }

    public BigDecimal getMaxDailyAmount() { return maxDailyAmount; }
    public void setMaxDailyAmount(BigDecimal maxDailyAmount) { this.maxDailyAmount = maxDailyAmount; }

    public int getMaxVelocityCountPerHour() { return maxVelocityCountPerHour; }
    public void setMaxVelocityCountPerHour(int maxVelocityCountPerHour) { this.maxVelocityCountPerHour = maxVelocityCountPerHour; }

    public int getNewAccountAgeHours() { return newAccountAgeHours; }
    public void setNewAccountAgeHours(int newAccountAgeHours) { this.newAccountAgeHours = newAccountAgeHours; }

    public BigDecimal getNewAccountMaxTransactionAmount() { return newAccountMaxTransactionAmount; }
    public void setNewAccountMaxTransactionAmount(BigDecimal newAccountMaxTransactionAmount) { this.newAccountMaxTransactionAmount = newAccountMaxTransactionAmount; }

    public BigDecimal getNewAccountMaxDailyAmount() { return newAccountMaxDailyAmount; }
    public void setNewAccountMaxDailyAmount(BigDecimal newAccountMaxDailyAmount) { this.newAccountMaxDailyAmount = newAccountMaxDailyAmount; }

    public int getVelocityWindowMinutes() { return velocityWindowMinutes; }
    public void setVelocityWindowMinutes(int velocityWindowMinutes) { this.velocityWindowMinutes = velocityWindowMinutes; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
