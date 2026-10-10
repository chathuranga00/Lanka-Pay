package com.lankapay.identity.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA entity for idn_otp_metadata.
 *
 * Holds per-user, per-purpose rate-limiting state:
 *  - {@code resendCooldownUntil}  – next allowed resend time (prevents spamming)
 *  - {@code dailyOtpCount}        – how many OTPs have been sent today
 *  - {@code dailyOtpDate}         – the date the counter belongs to (reset when it changes)
 */
@Entity
@Table(name = "idn_otp_metadata",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_idn_otp_metadata_user_purpose",
               columnNames = {"user_id", "purpose"}))
public class OtpMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OtpPurpose purpose;

    @Column(name = "resend_cooldown_until")
    private Instant resendCooldownUntil;

    @Column(name = "daily_otp_count", nullable = false)
    private int dailyOtpCount = 0;

    @Column(name = "daily_otp_date")
    private LocalDate dailyOtpDate;

    protected OtpMetadata() {}

    public OtpMetadata(User user, OtpPurpose purpose) {
        this.user    = user;
        this.purpose = purpose;
    }

    // ── Getters ────────────────────────────────────────────────

    public Long         getId()                   { return id; }
    public User         getUser()                 { return user; }
    public OtpPurpose   getPurpose()              { return purpose; }
    public Instant      getResendCooldownUntil()  { return resendCooldownUntil; }
    public int          getDailyOtpCount()        { return dailyOtpCount; }
    public LocalDate    getDailyOtpDate()         { return dailyOtpDate; }

    // ── Business helpers ───────────────────────────────────────

    /** True if a resend is not yet allowed. */
    public boolean isInCooldown() {
        return resendCooldownUntil != null && Instant.now().isBefore(resendCooldownUntil);
    }

    /** Set cooldown until {@code until}. */
    public void setCooldownUntil(Instant until) {
        this.resendCooldownUntil = until;
    }

    /**
     * Increment today's send count, resetting if the date has changed.
     * Returns the new count after incrementing.
     */
    public int incrementDailyCount(LocalDate today) {
        if (!today.equals(dailyOtpDate)) {
            dailyOtpCount = 0;
            dailyOtpDate  = today;
        }
        return ++dailyOtpCount;
    }
}
