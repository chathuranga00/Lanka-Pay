-- ============================================================
-- Lanka Pay – V3
-- Add login-lockout columns to idn_users.
-- Rename role enum values: USER -> CUSTOMER (no-op in MySQL
-- since stored as VARCHAR; update existing rows to be safe).
-- ============================================================

ALTER TABLE idn_users
    ADD COLUMN failed_login_attempts INT          NOT NULL DEFAULT 0    AFTER role,
    ADD COLUMN locked_until          DATETIME(6)  NULL                  AFTER failed_login_attempts;

-- Rename any legacy 'USER' role values to 'CUSTOMER'
UPDATE idn_users SET role = 'CUSTOMER' WHERE role = 'USER';

-- Rename any legacy 'ADMIN' role values (no-op, kept for clarity)
-- UPDATE idn_users SET role = 'ADMIN' WHERE role = 'ADMIN';
