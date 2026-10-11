-- ── V9: Add notification preferences, notification columns, and transfer bank codes ──

-- 1. Transfer bank codes for history filtering
ALTER TABLE trf_transfers
    ADD COLUMN sender_bank_code VARCHAR(20) NULL AFTER sender_id,
    ADD COLUMN receiver_bank_code VARCHAR(20) NULL AFTER receiver_id,
    ADD INDEX idx_trf_transfers_banks (sender_bank_code, receiver_bank_code);

-- 2. Enhanced notification columns (title, read tracking, retry counts)
ALTER TABLE ntf_notifications
    MODIFY COLUMN channel VARCHAR(20) NOT NULL COMMENT 'IN_APP | PUSH | EMAIL | SMS',
    ADD COLUMN title VARCHAR(100) NULL AFTER channel,
    ADD COLUMN is_read TINYINT(1) NOT NULL DEFAULT 0 AFTER simulated,
    ADD COLUMN read_at DATETIME(6) NULL AFTER is_read,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 AFTER read_at,
    ADD COLUMN failure_reason VARCHAR(255) NULL AFTER retry_count,
    ADD INDEX idx_ntf_notifications_read (user_id, is_read);

-- 3. Notification preferences per user
CREATE TABLE ntf_notification_preferences (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    in_app_enabled   TINYINT(1)   NOT NULL DEFAULT 1,
    push_enabled     TINYINT(1)   NOT NULL DEFAULT 1,
    email_enabled    TINYINT(1)   NOT NULL DEFAULT 1,
    sms_enabled      TINYINT(1)   NOT NULL DEFAULT 1,
    payment_alerts   TINYINT(1)   NOT NULL DEFAULT 1,
    security_alerts  TINYINT(1)   NOT NULL DEFAULT 1,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ntf_pref_user (user_id),
    CONSTRAINT fk_ntf_pref_user FOREIGN KEY (user_id) REFERENCES idn_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
