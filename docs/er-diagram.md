# Lanka Pay – ER Diagram

> **Draft** – will be verified against `docs/MASTER_PROMPT.md` section 15 once populated.

Table names use a module-prefix convention matching `architecture.md`.

---

## Mermaid ER Diagram

```mermaid
erDiagram

    %% ── identity module ───────────────────────────────────────
    idn_users {
        BIGINT      id              PK
        VARCHAR     phone           UK  "E.164 format"
        VARCHAR     full_name
        VARCHAR     nic_number      UK  "masked in logs"
        VARCHAR     password_hash       "BCrypt"
        VARCHAR     status              "PENDING_VERIFICATION | ACTIVE | FROZEN | DELETED"
        VARCHAR     role                "USER | ADMIN"
        TIMESTAMP   created_at
        TIMESTAMP   updated_at
    }

    idn_otps {
        BIGINT      id              PK
        BIGINT      user_id         FK
        VARCHAR     otp_hash            "BCrypt hash of 6-digit OTP"
        VARCHAR     purpose             "REGISTRATION | LOGIN | TRANSFER | PASSWORD_RESET"
        BOOLEAN     used
        TIMESTAMP   expires_at
        TIMESTAMP   created_at
    }

    idn_refresh_tokens {
        BIGINT      id              PK
        BIGINT      user_id         FK
        VARCHAR     token_hash      UK
        BOOLEAN     revoked
        TIMESTAMP   expires_at
        TIMESTAMP   created_at
    }

    %% ── registry module ────────────────────────────────────────
    reg_registry {
        BIGINT      id              PK
        BIGINT      user_id         FK  UK
        VARCHAR     phone           UK
        BOOLEAN     discoverable        "default true"
        TIMESTAMP   updated_at
    }

    %% ── bank-integration module ────────────────────────────────
    bnk_bank_accounts {
        BIGINT      id              PK
        BIGINT      user_id         FK
        VARCHAR     bank_code           "e.g. BOC, HNB, SIMULATED_BANK_01"
        VARCHAR     account_number_hash UK  "hashed; never stored plaintext"
        VARCHAR     masked_number       "last 4 digits only"
        VARCHAR     account_name
        VARCHAR     status              "ACTIVE | REMOVED"
        BOOLEAN     is_default
        TIMESTAMP   linked_at
        TIMESTAMP   updated_at
    }

    %% ── transfer module ────────────────────────────────────────
    trf_transfers {
        BIGINT      id              PK
        VARCHAR     reference       UK  "UUID"
        BIGINT      sender_id       FK
        BIGINT      receiver_id     FK
        DECIMAL     amount              "DECIMAL(15,2)"
        VARCHAR     currency            "always LKR"
        VARCHAR     status              "PENDING | COMPLETED | FAILED | REVERSED"
        VARCHAR     failure_reason
        VARCHAR     bank_tx_ref         "ref from BankAdapter (SIMULATED)"
        BOOLEAN     simulated           "always true in prototype"
        VARCHAR     initiated_via       "APP | QR"
        BIGINT      qr_id           FK  "nullable"
        TIMESTAMP   created_at
        TIMESTAMP   updated_at
    }

    trf_daily_limits {
        BIGINT      id              PK
        BIGINT      user_id         FK  UK "(user_id, date)"
        DATE        limit_date
        DECIMAL     total_sent          "DECIMAL(15,2)"
        DECIMAL     daily_cap           "DECIMAL(15,2); default 100000.00 LKR"
    }

    %% ── qr module ───────────────────────────────────────────────
    qr_codes {
        BIGINT      id              PK
        VARCHAR     qr_id           UK  "UUID"
        BIGINT      merchant_id     FK
        DECIMAL     amount              "DECIMAL(15,2)"
        VARCHAR     currency            "always LKR"
        VARCHAR     note
        VARCHAR     status              "ACTIVE | USED | EXPIRED | CANCELLED"
        TIMESTAMP   expires_at
        TIMESTAMP   created_at
    }

    %% ── notification module ─────────────────────────────────────
    ntf_notifications {
        BIGINT      id              PK
        BIGINT      user_id         FK
        VARCHAR     channel             "SMS | PUSH"
        VARCHAR     type                "OTP | PAYMENT_SENT | PAYMENT_RECEIVED | ACCOUNT_LINKED"
        VARCHAR     status              "PENDING | SENT | FAILED"
        TEXT        message
        BOOLEAN     simulated
        TIMESTAMP   sent_at
        TIMESTAMP   created_at
    }

    %% ── audit module ────────────────────────────────────────────
    aud_audit_log {
        BIGINT      id              PK
        BIGINT      actor_id        FK  "nullable for system events"
        VARCHAR     event_type          "LOGIN_SUCCESS | TRANSFER_COMPLETED | etc."
        VARCHAR     entity_type         "USER | TRANSFER | QR_CODE | etc."
        BIGINT      entity_id
        TEXT        detail_json         "sanitised – no PII, no full account numbers"
        VARCHAR     ip_address
        TIMESTAMP   occurred_at
    }

    %% ── Relationships ───────────────────────────────────────────
    idn_users       ||--o{ idn_otps             : "has"
    idn_users       ||--o{ idn_refresh_tokens   : "has"
    idn_users       ||--|| reg_registry         : "has one"
    idn_users       ||--o{ bnk_bank_accounts    : "owns"
    idn_users       ||--o{ trf_transfers        : "sends"
    idn_users       ||--o{ trf_transfers        : "receives"
    idn_users       ||--|| trf_daily_limits     : "has"
    idn_users       ||--o{ qr_codes             : "generates"
    idn_users       ||--o{ ntf_notifications    : "receives"
    idn_users       ||--o{ aud_audit_log        : "actor"
    trf_transfers   ||--o| qr_codes             : "triggered by"
```

---

## Table Notes & Constraints

### idn_users
- `phone` – unique, stored in E.164 format (`+94XXXXXXXXX`). Never log the full value.
- `nic_number` – unique index; masked to first 4 chars + `****` in all API responses.
- `password_hash` – BCrypt (cost ≥ 12). Plain-text password never persisted.
- `status` transitions: `PENDING_VERIFICATION → ACTIVE → FROZEN / DELETED`.
- Soft-delete only (`status=DELETED`); rows are never physically removed.

### idn_otps
- `otp_hash` – BCrypt hash of the 6-digit code; plaintext OTP is discarded after hashing.
- `expires_at` – 5 minutes after creation; enforced at application level and via a scheduled cleanup job.
- Composite index on `(user_id, purpose, used, expires_at)` for fast lookup.

### idn_refresh_tokens
- `token_hash` – SHA-256 of the raw token; raw token is never stored.
- Revoked on logout, password change, or account freeze.

### bnk_bank_accounts
- `account_number_hash` – SHA-256 of bank_code + account_number; used for duplicate detection.
- `masked_number` – last 4 digits only; the only account-number data shown in UI or logs.
- `bank_code` values for simulated banks are prefixed `SIMULATED_`.

### trf_transfers
- `amount` – `DECIMAL(15,2)`; application enforces `amount > 0`.
- `status=COMPLETED` is set **only** after BankAdapter returns explicit success for both legs.
- `simulated=true` is always set in the university prototype.
- Index on `(sender_id, created_at)` and `(receiver_id, created_at)` for history queries.
- Index on `reference` (unique) for idempotency checks.

### trf_daily_limits
- Composite unique key `(user_id, limit_date)`.
- Application increments `total_sent` atomically (optimistic lock or `SELECT FOR UPDATE`).

### qr_codes
- `expires_at` default is 15 minutes after creation.
- `status` transitions: `ACTIVE → USED` (paid) or `ACTIVE → EXPIRED` (scheduled job) or `ACTIVE → CANCELLED`.
- Index on `(qr_id, status, expires_at)`.

### ntf_notifications
- `simulated=true` in all prototype environments.
- Retained for 90 days then purged by a scheduled job.

### aud_audit_log
- **Append-only** – no UPDATE or DELETE on this table.
- `detail_json` must be sanitised before insert: no full account numbers, no OTP values, no plaintext passwords.
- Partitioned by `occurred_at` (monthly) for large-volume query performance.
- Index on `(actor_id, event_type, occurred_at)`.
