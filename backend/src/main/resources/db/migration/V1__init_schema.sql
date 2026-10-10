-- ============================================================
-- Lanka Pay – V1 baseline schema
-- All tables, foreign keys, indexes.
-- Flyway owns this schema; Hibernate is set to validate-only.
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ── identity module ──────────────────────────────────────────

CREATE TABLE idn_users (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    phone         VARCHAR(20)     NOT NULL COMMENT 'E.164 format, e.g. +94771234567',
    full_name     VARCHAR(100)    NOT NULL,
    nic_number    VARCHAR(20)     NOT NULL COMMENT 'Masked in logs and API responses',
    password_hash VARCHAR(255)    NOT NULL COMMENT 'BCrypt hash, cost >= 12',
    status        VARCHAR(30)     NOT NULL DEFAULT 'PENDING_VERIFICATION'
                                  COMMENT 'PENDING_VERIFICATION | ACTIVE | FROZEN | DELETED',
    role          VARCHAR(20)     NOT NULL DEFAULT 'USER'
                                  COMMENT 'USER | ADMIN',
    created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                  ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_idn_users_phone      (phone),
    UNIQUE KEY uk_idn_users_nic        (nic_number),
    INDEX        idx_idn_users_status  (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE idn_otps (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    otp_hash   VARCHAR(255) NOT NULL COMMENT 'BCrypt hash of 6-digit OTP; plaintext discarded',
    purpose    VARCHAR(30)  NOT NULL COMMENT 'REGISTRATION | LOGIN | TRANSFER | PASSWORD_RESET',
    used       TINYINT(1)   NOT NULL DEFAULT 0,
    expires_at DATETIME(6)  NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_idn_otps_lookup (user_id, purpose, used, expires_at),
    CONSTRAINT fk_idn_otps_user FOREIGN KEY (user_id)
        REFERENCES idn_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE idn_refresh_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(255) NOT NULL COMMENT 'SHA-256 of raw token; raw token never stored',
    revoked     TINYINT(1)   NOT NULL DEFAULT 0,
    expires_at  DATETIME(6)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_idn_refresh_tokens_hash (token_hash),
    INDEX       idx_idn_refresh_tokens_user (user_id),
    CONSTRAINT fk_idn_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES idn_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── registry module ───────────────────────────────────────────

CREATE TABLE reg_registry (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    phone        VARCHAR(20) NOT NULL,
    discoverable TINYINT(1)  NOT NULL DEFAULT 1 COMMENT 'false = user opted out of phone lookup',
    updated_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                             ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_reg_registry_user  (user_id),
    UNIQUE KEY uk_reg_registry_phone (phone),
    CONSTRAINT fk_reg_registry_user FOREIGN KEY (user_id)
        REFERENCES idn_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── bank-integration module ───────────────────────────────────

CREATE TABLE bnk_bank_institutions (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    bank_code    VARCHAR(50)  NOT NULL COMMENT 'Prefix SIMULATED_ for all prototype banks',
    display_name VARCHAR(100) NOT NULL,
    simulated    TINYINT(1)   NOT NULL DEFAULT 1,
    active       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_bnk_institutions_code (bank_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bnk_bank_accounts (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    bank_code           VARCHAR(50)  NOT NULL COMMENT 'References bnk_bank_institutions.bank_code',
    account_number_hash VARCHAR(255) NOT NULL COMMENT 'SHA-256(bank_code + accountNumber); for duplicate detection',
    masked_number       VARCHAR(10)  NOT NULL COMMENT 'Last 4 digits only, e.g. ****7890',
    account_name        VARCHAR(100) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | REMOVED',
    is_default          TINYINT(1)   NOT NULL DEFAULT 0,
    version             BIGINT       NOT NULL DEFAULT 0 COMMENT 'Optimistic locking',
    linked_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                     ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_bnk_bank_accounts_hash (account_number_hash),
    INDEX      idx_bnk_bank_accounts_user (user_id, status),
    CONSTRAINT fk_bnk_bank_accounts_user FOREIGN KEY (user_id)
        REFERENCES idn_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── qr module (defined before transfer because transfer references it) ──

CREATE TABLE qr_codes (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    qr_id       VARCHAR(36)     NOT NULL COMMENT 'UUID, used in QR payload',
    merchant_id BIGINT          NOT NULL,
    amount      DECIMAL(15, 2)  NOT NULL,
    currency    VARCHAR(3)      NOT NULL DEFAULT 'LKR',
    note        VARCHAR(255)    NULL,
    status      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE'
                                COMMENT 'ACTIVE | USED | EXPIRED | CANCELLED',
    expires_at  DATETIME(6)     NOT NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_qr_codes_qr_id           (qr_id),
    INDEX      idx_qr_codes_lookup         (qr_id, status, expires_at),
    INDEX      idx_qr_codes_merchant       (merchant_id),
    CONSTRAINT fk_qr_codes_merchant FOREIGN KEY (merchant_id)
        REFERENCES idn_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── transfer module ───────────────────────────────────────────

CREATE TABLE trf_transfers (
    id             BIGINT          NOT NULL AUTO_INCREMENT,
    reference      VARCHAR(36)     NOT NULL COMMENT 'UUID – idempotency key',
    sender_id      BIGINT          NOT NULL,
    receiver_id    BIGINT          NOT NULL,
    amount         DECIMAL(15, 2)  NOT NULL,
    currency       VARCHAR(3)      NOT NULL DEFAULT 'LKR',
    status         VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                                   COMMENT 'PENDING | COMPLETED | FAILED | REVERSED',
    failure_reason VARCHAR(255)    NULL,
    bank_tx_ref    VARCHAR(100)    NULL COMMENT 'Reference returned by BankAdapter (SIMULATED)',
    simulated      TINYINT(1)      NOT NULL DEFAULT 1 COMMENT 'Always true in this prototype',
    initiated_via  VARCHAR(10)     NOT NULL DEFAULT 'APP' COMMENT 'APP | QR',
    qr_id          BIGINT          NULL,
    version        BIGINT          NOT NULL DEFAULT 0 COMMENT 'Optimistic locking',
    created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                   ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_trf_transfers_reference       (reference),
    INDEX      idx_trf_transfers_sender         (sender_id, created_at),
    INDEX      idx_trf_transfers_receiver       (receiver_id, created_at),
    INDEX      idx_trf_transfers_status         (status),
    CONSTRAINT fk_trf_transfers_sender   FOREIGN KEY (sender_id)
        REFERENCES idn_users (id),
    CONSTRAINT fk_trf_transfers_receiver FOREIGN KEY (receiver_id)
        REFERENCES idn_users (id),
    CONSTRAINT fk_trf_transfers_qr       FOREIGN KEY (qr_id)
        REFERENCES qr_codes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE trf_daily_limits (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_id     BIGINT          NOT NULL,
    limit_date  DATE            NOT NULL,
    total_sent  DECIMAL(15, 2)  NOT NULL DEFAULT 0.00,
    daily_cap   DECIMAL(15, 2)  NOT NULL DEFAULT 100000.00 COMMENT 'Default 100,000 LKR',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trf_daily_limits_user_date (user_id, limit_date),
    CONSTRAINT fk_trf_daily_limits_user FOREIGN KEY (user_id)
        REFERENCES idn_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── notification module ───────────────────────────────────────

CREATE TABLE ntf_notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    channel    VARCHAR(10)  NOT NULL COMMENT 'SMS | PUSH',
    type       VARCHAR(40)  NOT NULL COMMENT 'OTP | PAYMENT_SENT | PAYMENT_RECEIVED | ACCOUNT_LINKED',
    status     VARCHAR(10)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | SENT | FAILED',
    message    TEXT         NOT NULL,
    simulated  TINYINT(1)   NOT NULL DEFAULT 1,
    sent_at    DATETIME(6)  NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_ntf_notifications_user    (user_id, created_at),
    INDEX idx_ntf_notifications_status  (status),
    CONSTRAINT fk_ntf_notifications_user FOREIGN KEY (user_id)
        REFERENCES idn_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── audit module ──────────────────────────────────────────────

CREATE TABLE aud_audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id    BIGINT       NULL COMMENT 'NULL for system-generated events',
    event_type  VARCHAR(60)  NOT NULL COMMENT 'e.g. LOGIN_SUCCESS, TRANSFER_COMPLETED',
    entity_type VARCHAR(40)  NOT NULL COMMENT 'e.g. USER, TRANSFER, QR_CODE',
    entity_id   BIGINT       NULL,
    detail_json TEXT         NULL COMMENT 'Sanitised JSON – no PII, no account numbers',
    ip_address  VARCHAR(45)  NULL COMMENT 'IPv4 or IPv6',
    occurred_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_aud_audit_log_actor       (actor_id, event_type, occurred_at),
    INDEX idx_aud_audit_log_entity      (entity_type, entity_id),
    INDEX idx_aud_audit_log_occurred_at (occurred_at)
    -- No FK on actor_id intentionally: audit rows must survive user deletion
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
