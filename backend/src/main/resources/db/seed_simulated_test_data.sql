-- ==============================================================================
-- Lanka Pay – Standalone Simulated Test Data Seed Script
-- ⚠ UNIVERSITY PROTOTYPE / TEST PURPOSES ONLY
--
-- This script seeds the test personas and accounts per Section 24 specification:
--   1. Kasun Perera [TEST] (+94771000001) – 3 accounts at Banks A, B, C (Default: Bank B)
--   2. Nimal Silva [TEST]   (+94771000002) – 1 account at Bank A (Default: Bank A)
--   3. Fail Scenario User [TEST] (+94771000003) – Account triggering HARD_FAILURE
--   4. Timeout Scenario User [TEST] (+94771000004) – Account triggering TIMEOUT_SUCCEEDED
--   5. Reversal Scenario User [TEST] (+94771000005) – Account triggering LATER_REVERSAL
--   6. Operations Administrator [TEST] (+94770000001) – Role: ADMIN
--
-- Default PIN for all accounts: "123456" (BCrypt hash below with cost factor 12)
-- ==============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ── 1. Seed Users ─────────────────────────────────────────────────────────────
-- BCrypt hash for "123456": $2a$12$K8q1rU8o9qN3N.h7Yp1jU.8n7G2F6C7D8E9A0B1C2D3E4F5G6H7I8
INSERT INTO idn_users (id, phone, full_name, nic_number, password_hash, status, role)
VALUES
    (101, '+94771000001', 'Kasun Perera [TEST]', '199010000001', '$2a$12$K8q1rU8o9qN3N.h7Yp1jU.8n7G2F6C7D8E9A0B1C2D3E4F5G6H7I8', 'ACTIVE', 'CUSTOMER'),
    (102, '+94771000002', 'Nimal Silva [TEST]', '199010000002', '$2a$12$K8q1rU8o9qN3N.h7Yp1jU.8n7G2F6C7D8E9A0B1C2D3E4F5G6H7I8', 'ACTIVE', 'CUSTOMER'),
    (103, '+94771000003', 'Fail Scenario User [TEST]', '199010000003', '$2a$12$K8q1rU8o9qN3N.h7Yp1jU.8n7G2F6C7D8E9A0B1C2D3E4F5G6H7I8', 'ACTIVE', 'CUSTOMER'),
    (104, '+94771000004', 'Timeout Scenario User [TEST]', '199010000004', '$2a$12$K8q1rU8o9qN3N.h7Yp1jU.8n7G2F6C7D8E9A0B1C2D3E4F5G6H7I8', 'ACTIVE', 'CUSTOMER'),
    (105, '+94771000005', 'Reversal Scenario User [TEST]', '199010000005', '$2a$12$K8q1rU8o9qN3N.h7Yp1jU.8n7G2F6C7D8E9A0B1C2D3E4F5G6H7I8', 'ACTIVE', 'CUSTOMER'),
    (100, '+94770000001', 'Operations Administrator [TEST]', '198000000001', '$2a$12$K8q1rU8o9qN3N.h7Yp1jU.8n7G2F6C7D8E9A0B1C2D3E4F5G6H7I8', 'ACTIVE', 'ADMIN')
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), status = VALUES(status);

-- ── 2. Seed Registry ──────────────────────────────────────────────────────────
INSERT INTO reg_registry (user_id, phone, discoverable)
VALUES
    (101, '+94771000001', 1),
    (102, '+94771000002', 1),
    (103, '+94771000003', 1),
    (104, '+94771000004', 1),
    (105, '+94771000005', 1)
ON DUPLICATE KEY UPDATE discoverable = VALUES(discoverable);

-- ── 3. Seed Bank Accounts ─────────────────────────────────────────────────────
-- Kasun: 3 accounts (Bank A, Bank B, Bank C). Default is Bank B.
INSERT INTO bnk_bank_accounts (id, user_id, bank_code, account_number_hash, masked_number, account_name, label, is_default, discoverable, receiving_enabled, status)
VALUES
    (201, 101, 'SIMULATED_BANK_A', SHA2('SIMULATED_BANK_A101001', 256), '****1001', 'Kasun Perera [TEST]', 'Savings Account A [TEST]', 0, 1, 1, 'ACTIVE'),
    (202, 101, 'SIMULATED_BANK_B', SHA2('SIMULATED_BANK_B201001', 256), '****1001', 'Kasun Perera [TEST]', 'Salary Account B [TEST]', 1, 1, 1, 'ACTIVE'),
    (203, 101, 'SIMULATED_BANK_C', SHA2('SIMULATED_BANK_C301001', 256), '****1001', 'Kasun Perera [TEST]', 'Investment Account C [TEST]', 0, 1, 1, 'ACTIVE'),

    -- Nimal: 1 account (Bank A)
    (204, 102, 'SIMULATED_BANK_A', SHA2('SIMULATED_BANK_A101002', 256), '****1002', 'Nimal Silva [TEST]', 'Primary Account [TEST]', 1, 1, 1, 'ACTIVE'),

    -- Scenario Accounts:
    (205, 103, 'SIMULATED_BANK_A', SHA2('SIMULATED_BANK_A100400', 256), '****0400', 'Fail Account [TEST]', 'Hard Failure Account [TEST]', 1, 1, 1, 'ACTIVE'),
    (206, 104, 'SIMULATED_BANK_A', SHA2('SIMULATED_BANK_A100200', 256), '****0200', 'Timeout Account [TEST]', 'Timeout Trigger Account [TEST]', 1, 1, 1, 'ACTIVE'),
    (207, 105, 'SIMULATED_BANK_A', SHA2('SIMULATED_BANK_A100500', 256), '****0500', 'Reversal Account [TEST]', 'Reversal Trigger Account [TEST]', 1, 1, 1, 'ACTIVE')
ON DUPLICATE KEY UPDATE account_name = VALUES(account_name), is_default = VALUES(is_default);

-- ── 4. Seed Consents ──────────────────────────────────────────────────────────
INSERT INTO bnk_account_consents (user_id, bank_account_id, consent_type, consent_given, consent_ip)
VALUES
    (101, 201, 'LINK_ACCOUNT', 1, '127.0.0.1'),
    (101, 202, 'LINK_ACCOUNT', 1, '127.0.0.1'),
    (101, 203, 'LINK_ACCOUNT', 1, '127.0.0.1'),
    (102, 204, 'LINK_ACCOUNT', 1, '127.0.0.1'),
    (103, 205, 'LINK_ACCOUNT', 1, '127.0.0.1'),
    (104, 206, 'LINK_ACCOUNT', 1, '127.0.0.1'),
    (105, 207, 'LINK_ACCOUNT', 1, '127.0.0.1')
ON DUPLICATE KEY UPDATE consent_given = VALUES(consent_given);

SET FOREIGN_KEY_CHECKS = 1;
