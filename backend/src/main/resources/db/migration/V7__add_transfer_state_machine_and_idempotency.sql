-- ── V7: Add transfer state machine, transaction events, and idempotency keys ──

ALTER TABLE trf_transfers
    MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
    COMMENT 'QUOTED | PENDING | PROCESSING | COMPLETED | FAILED | REQUIRES_RECONCILIATION | REVERSAL_PENDING | REVERSED';

ALTER TABLE trf_transfers
    ADD COLUMN idempotency_key VARCHAR(64) NULL AFTER reference,
    ADD COLUMN provider_reference VARCHAR(100) NULL AFTER bank_tx_ref,
    ADD COLUMN note VARCHAR(255) NULL AFTER provider_reference,
    ADD CONSTRAINT uk_trf_transfers_idempotency_key UNIQUE (idempotency_key);

-- ── Append-only transaction events audit log ──────────────────────

CREATE TABLE trf_transaction_events (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    transfer_id BIGINT       NOT NULL,
    from_status VARCHAR(30)  NULL,
    to_status   VARCHAR(30)  NOT NULL,
    reason      VARCHAR(100) NULL COMMENT 'Machine-readable reason e.g. BANK_CONFIRMED, TIMEOUT',
    detail_json TEXT         NULL COMMENT 'Sanitised JSON detail – no PII, no full account numbers',
    occurred_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_trf_tx_events_transfer (transfer_id, occurred_at),
    INDEX idx_trf_tx_events_status   (to_status),
    CONSTRAINT fk_trf_tx_events_transfer FOREIGN KEY (transfer_id)
        REFERENCES trf_transfers (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Idempotency keys tracking ───────────────────────────────────

CREATE TABLE trf_idempotency_keys (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    idempotency_key VARCHAR(64)  NOT NULL,
    payload_hash    VARCHAR(64)  NOT NULL COMMENT 'SHA-256 base64url of payload',
    transfer_id     BIGINT       NULL,
    response_json   TEXT         NULL COMMENT 'Serialised response for replay',
    http_status     INT          NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_trf_idempotency_key (idempotency_key),
    CONSTRAINT fk_trf_idempotency_transfer FOREIGN KEY (transfer_id)
        REFERENCES trf_transfers (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
