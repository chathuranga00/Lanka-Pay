-- ── V8: Section 10 – QR references and payment requests ────────────────────────

-- Update qr_codes to support static QR, opaque reference, and payload
ALTER TABLE qr_codes
    ADD COLUMN reference VARCHAR(64) NULL AFTER id;

UPDATE qr_codes SET reference = qr_id WHERE reference IS NULL;

ALTER TABLE qr_codes
    MODIFY COLUMN reference VARCHAR(64) NOT NULL,
    ADD CONSTRAINT uk_qr_codes_reference UNIQUE (reference),
    ADD COLUMN qr_type VARCHAR(20) NOT NULL DEFAULT 'DYNAMIC' AFTER qr_id,
    ADD COLUMN one_time_use TINYINT(1) NOT NULL DEFAULT 1 AFTER expires_at,
    ADD COLUMN payload VARCHAR(255) NULL AFTER one_time_use,
    MODIFY COLUMN amount DECIMAL(15, 2) NULL,
    MODIFY COLUMN expires_at DATETIME(6) NULL;

-- Payment requests table
CREATE TABLE pmt_payment_requests (
    id                    BIGINT          NOT NULL AUTO_INCREMENT,
    reference             VARCHAR(64)     NOT NULL,
    requester_id          BIGINT          NOT NULL,
    payer_id              BIGINT          NULL,
    amount                DECIMAL(15, 2)  NOT NULL,
    currency              VARCHAR(3)      NOT NULL DEFAULT 'LKR',
    note                  VARCHAR(255)    NULL,
    status                VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                                          COMMENT 'PENDING | COMPLETED | EXPIRED | CANCELLED',
    qr_code_id            BIGINT          NULL,
    fulfilled_transfer_id BIGINT          NULL,
    expires_at            DATETIME(6)     NOT NULL,
    created_at            DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                          ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_pmt_payment_requests_ref (reference),
    INDEX idx_pmt_payment_requests_requester (requester_id),
    INDEX idx_pmt_payment_requests_status    (status),
    CONSTRAINT fk_pmt_req_requester FOREIGN KEY (requester_id)
        REFERENCES idn_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_pmt_req_payer FOREIGN KEY (payer_id)
        REFERENCES idn_users (id) ON DELETE SET NULL,
    CONSTRAINT fk_pmt_req_qr FOREIGN KEY (qr_code_id)
        REFERENCES qr_codes (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
