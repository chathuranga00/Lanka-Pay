-- ── V10: Add risk rules configuration and fraud alerts ──

CREATE TABLE rsk_risk_rules (
    id                                  BIGINT         NOT NULL AUTO_INCREMENT,
    max_transaction_amount              DECIMAL(15, 2) NOT NULL DEFAULT 50000.00,
    max_daily_amount                    DECIMAL(15, 2) NOT NULL DEFAULT 100000.00,
    max_velocity_count_per_hour         INT            NOT NULL DEFAULT 5,
    new_account_age_hours               INT            NOT NULL DEFAULT 24,
    new_account_max_transaction_amount  DECIMAL(15, 2) NOT NULL DEFAULT 10000.00,
    new_account_max_daily_amount        DECIMAL(15, 2) NOT NULL DEFAULT 25000.00,
    velocity_window_minutes             INT            NOT NULL DEFAULT 60,
    enabled                             TINYINT(1)     NOT NULL DEFAULT 1,
    created_at                          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by                          BIGINT         NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO rsk_risk_rules (id, max_transaction_amount, max_daily_amount, max_velocity_count_per_hour, new_account_age_hours, new_account_max_transaction_amount, new_account_max_daily_amount, velocity_window_minutes, enabled)
VALUES (1, 50000.00, 100000.00, 5, 24, 10000.00, 25000.00, 60, 1);

CREATE TABLE rsk_fraud_alerts (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    user_id          BIGINT        NOT NULL,
    transfer_id      BIGINT        NULL,
    alert_type       VARCHAR(50)   NOT NULL COMMENT 'VELOCITY_EXCEEDED | NEW_ACCOUNT_LIMIT_EXCEEDED | PER_TRANSACTION_LIMIT_EXCEEDED | DAILY_LIMIT_EXCEEDED | SUSPICIOUS_ACTIVITY',
    severity         VARCHAR(20)   NOT NULL DEFAULT 'MEDIUM' COMMENT 'LOW | MEDIUM | HIGH | CRITICAL',
    status           VARCHAR(20)   NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN | UNDER_REVIEW | RESOLVED | DISMISSED',
    description      VARCHAR(500)  NOT NULL,
    metadata_json    TEXT          NULL,
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at      DATETIME(6)   NULL,
    resolved_by      BIGINT        NULL,
    resolution_notes VARCHAR(500)  NULL,
    PRIMARY KEY (id),
    INDEX idx_rsk_fraud_alerts_user (user_id, created_at),
    INDEX idx_rsk_fraud_alerts_status (status, severity),
    CONSTRAINT fk_rsk_fraud_alerts_user FOREIGN KEY (user_id) REFERENCES idn_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_rsk_fraud_alerts_transfer FOREIGN KEY (transfer_id) REFERENCES trf_transfers (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
