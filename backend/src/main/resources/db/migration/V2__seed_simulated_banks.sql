-- ============================================================
-- Lanka Pay – V2 seed data
-- 3 SIMULATED bank institutions.
--
-- ⚠ UNIVERSITY PROTOTYPE – all banks are SIMULATED.
-- These are NOT real Sri Lankan banks.
-- ============================================================

INSERT INTO bnk_bank_institutions (bank_code, display_name, simulated, active)
VALUES
    ('SIMULATED_BANK_A', 'Simulated Bank A [SIMULATED]', 1, 1),
    ('SIMULATED_BANK_B', 'Simulated Bank B [SIMULATED]', 1, 1),
    ('SIMULATED_BANK_C', 'Simulated Bank C [SIMULATED]', 1, 1);
