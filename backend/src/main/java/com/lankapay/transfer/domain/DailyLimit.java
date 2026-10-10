package com.lankapay.transfer.domain;

import com.lankapay.identity.domain.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * JPA entity for trf_daily_limits.
 * Tracks how much a user has sent today against their daily cap.
 * Unique constraint: (user_id, limit_date).
 */
@Entity
@Table(name = "trf_daily_limits",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_trf_daily_limits_user_date",
               columnNames = {"user_id", "limit_date"}))
public class DailyLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "limit_date", nullable = false)
    private LocalDate limitDate;

    @Column(name = "total_sent", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalSent = BigDecimal.ZERO;

    /** Default 100,000 LKR. Can be adjusted per user in future. */
    @Column(name = "daily_cap", nullable = false, precision = 15, scale = 2)
    private BigDecimal dailyCap = new BigDecimal("100000.00");

    protected DailyLimit() {}

    public DailyLimit(User user, LocalDate limitDate) {
        this.user = user;
        this.limitDate = limitDate;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public LocalDate getLimitDate() { return limitDate; }
    public BigDecimal getTotalSent() { return totalSent; }
    public BigDecimal getDailyCap() { return dailyCap; }

    public boolean hasCapacity(BigDecimal amount) {
        return totalSent.add(amount).compareTo(dailyCap) <= 0;
    }

    /** Atomically add to totalSent – caller must hold a lock on this row. */
    public void addSent(BigDecimal amount) {
        this.totalSent = this.totalSent.add(amount);
    }
}
