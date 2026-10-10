-- ============================================================
-- Lanka Pay – V5
-- Add account management features:
--   1. Extend bnk_bank_accounts with label, discoverable,
--      receiving_enabled, encrypted_account_reference.
--   2. Create bnk_account_consents table for explicit consent records.
-- ============================================================

-- ── Extend bnk_bank_accounts ─────────────────────────────────

ALTER TABLE bnk_bank_accounts
    ADD COLUMN label                       VARCHAR(80)  NULL
        COMMENT 'User-assigned label, e.g. "My Savings"'
        AFTER account_name,

    ADD COLUMN discoverable                TINYINT(1)   NOT NULL DEFAULT 1
        COMMENT 'Whether this account can be found in the payment registry'
        AFTER label,

    ADD COLUMN receiving_enabled           TINYINT(1)   NOT NULL DEFAULT 1
        COMMENT 'Whether this account can receive incoming transfers'
        AFTER discoverable,

    ADD COLUMN encrypted_account_reference TEXT         NULL
        COMMENT 'AES-GCM encrypted bank account reference; decrypted only in memory'
        AFTER receiving_enabled;

-- ── Consent records ───────────────────────────────────────────

CREATE TABLE bnk_account_consents (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    bank_account_id   BIGINT       NOT NULL,
    consent_type      VARCHAR(40)  NOT NULL
        COMMENT 'LINK_ACCOUNT | REMOVE_ACCOUNT | SET_DEFAULT | RECEIVE_FUNDS',
    consent_given     TINYINT(1)   NOT NULL DEFAULT 0,
    consent_ip        VARCHAR(45)  NULL,
    consented_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_bnk_consents_account (bank_account_id),
    INDEX idx_bnk_consents_user    (user_id),
    CONSTRAINT fk_bnk_consents_user    FOREIGN KEY (user_id)
        REFERENCES idn_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_bnk_consents_account FOREIGN KEY (bank_account_id)
        REFERENCES bnk_bank_accounts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
