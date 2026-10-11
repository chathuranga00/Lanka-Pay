package com.lankapay.admin.domain;

import java.math.BigDecimal;
import java.time.Instant;

public class RiskRules {

    private Long id;

    private BigDecimal maxTransactionAmount = new BigDecimal("50000.00");

    private BigDecimal maxDailyAmount = new BigDecimal("100000.00");

    private int maxVelocityCountPerHour = 5;

    private int newAccountAgeHours = 24;

    private BigDecimal newAccountMaxTransactionAmount = new BigDecimal("10000.00");

    private BigDecimal newAccountMaxDailyAmount = new BigDecimal("25000.00");

    private int velocityWindowMinutes = 60;

    private boolean enabled = true;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

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
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
