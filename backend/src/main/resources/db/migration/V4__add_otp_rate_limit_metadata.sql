-- ============================================================
-- Lanka Pay – V4
-- Add per-user OTP rate-limiting metadata table.
-- Tracks resend cooldown and daily OTP cap per (user, purpose).
-- ============================================================

CREATE TABLE idn_otp_metadata (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    purpose             VARCHAR(30)  NOT NULL  COMMENT 'Matches OtpPurpose enum',
    resend_cooldown_until DATETIME(6) NULL      COMMENT 'Cannot resend before this instant',
    daily_otp_count     INT          NOT NULL DEFAULT 0
                                              COMMENT 'OTPs sent today for this purpose',
    daily_otp_date      DATE         NULL      COMMENT 'Date the counter was last reset',
    PRIMARY KEY (id),
    UNIQUE KEY uk_idn_otp_metadata_user_purpose (user_id, purpose),
    INDEX      idx_idn_otp_metadata_user        (user_id),
    CONSTRAINT fk_idn_otp_metadata_user FOREIGN KEY (user_id)
        REFERENCES idn_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
