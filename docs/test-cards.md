# Lanka Pay – Simulated Vault Test Cards

> **TEST DATA ONLY.**
> All numbers below are Luhn-valid 16-digit values starting with `99`.
> The `99` prefix is not assigned to any real card network; these numbers
> are impossible to confuse with live payment credentials.
>
> ⚠ Never enter a real card number into the tokenize endpoint.
> The vault rejects every number that is not in this table.

---

## How to use

1. Start the backend (`mvn spring-boot:run`, `app.mode=SIMULATED`).
2. Log in as a test user to obtain a JWT.
3. Call `POST /sim-vault/v1/tokenize` with `Authorization: Bearer <jwt>`.
4. Use the card number, expiry, and CVV from the table below.
5. The endpoint returns an opaque `cardToken` (valid for 15 minutes).

---

## Test Card Table

| # | Card Number | Exp | CVV | Holder Name | Scenario | Expected outcome |
|---|---|---|---|---|---|---|
| 1 | `9900000000000010` | 12/27 | 111 | Test Approved | **ALWAYS_APPROVED** | Authorization succeeds immediately |
| 2 | `9900000000000028` | 12/27 | 222 | Test Insufficient | **INSUFFICIENT_FUNDS** | Authorization declined — not enough balance |
| 3 | `9900000000000069` | 01/20 | 333 | Test Expired | **CARD_EXPIRED** | Authorization declined — card expiry is in the past |
| 4 | `9900000000000119` | 12/27 | 444 | Test OTP Challenge | **REQUIRES_OTP_CHALLENGE** | Authorization paused — bank OTP required before proceeding |
| 5 | `9900000000000994` | 12/27 | 555 | Test Timeout | **PROVIDER_TIMEOUT_BUT_CHARGED** | Adapter returns TIMEOUT; polling `queryPayment` returns APPROVED |
| 6 | `9900000000001232` | 12/27 | 666 | Test Delayed | **DELAYED_CONFIRMATION** | Adapter returns PENDING; polling `queryPayment` returns APPROVED |
| 7 | `9900000000002347` | 12/27 | 777 | Test Hard Decline | **HARD_DECLINE** | Authorization declined permanently — card flagged stolen/blocked |
| 8 | `9900000000003451` | 12/27 | 888 | Test Reversal | **LATER_REVERSAL** | Authorization succeeds; polling `queryPayment` later returns REVERSED |

---

## Luhn Verification

All 8 numbers pass the standard Luhn check (verified manually):

| Card Number | Last 4 | Check digit |
|---|---|---|
| `9900000000000010` | 0010 | 0 |
| `9900000000000028` | 0028 | 8 |
| `9900000000000069` | 0069 | 9 |
| `9900000000000119` | 0119 | 9 |
| `9900000000000994` | 0994 | 4 |
| `9900000000001232` | 1232 | 2 |
| `9900000000002347` | 2347 | 7 |
| `9900000000003451` | 3451 | 1 |

---

## What the vault rejects

- Any number NOT in the table above.
- Numbers that fail the Luhn check.
- Expiry month outside `01–12`.
- CVV not 3–4 digits.
- Any real card number (this is intentional — the vault only accepts its own seed).

The rejection message is always the same regardless of which check failed:

> `"Test cards only. Never enter a real card number."`

This prevents enumeration of which validation failed.

---

## Scenario Details

### ALWAYS_APPROVED (card 1, last4 = 0010)
Normal happy path. Use this for integration testing of the full transfer flow.

### INSUFFICIENT_FUNDS (card 2, last4 = 0028)
Simulates a declined authorization. The transfer is marked FAILED.

### CARD_EXPIRED (card 3, last4 = 0069)
Expiry date is 01/2020 (past). Vault's `isExpired()` triggers a CARD_EXPIRED decline.

### REQUIRES_OTP_CHALLENGE (card 4, last4 = 0119)
Pauses authorization and sends a `VIRTUAL_CARD_CHALLENGE` OTP to the user's phone.
Visible in backend console in TEST mode.

### PROVIDER_TIMEOUT_BUT_CHARGED (card 5, last4 = 0994)
`authorize()` returns TIMEOUT → transfer moves to `REQUIRES_RECONCILIATION`.
`queryPayment()` returns APPROVED — card was actually charged.

### DELAYED_CONFIRMATION (card 6, last4 = 1232)
`authorize()` returns PENDING. `queryPayment()` returns APPROVED.

### HARD_DECLINE (card 7, last4 = 2347)
Permanent decline — card flagged stolen/blocked. Transfer marked FAILED, no retry.

### LATER_REVERSAL (card 8, last4 = 3451)
`authorize()` returns APPROVED initially.
`queryPayment()` later returns REVERSED — issuer reversed the charge.
Transfer moves to `REQUIRES_RECONCILIATION` → `REVERSED`.
