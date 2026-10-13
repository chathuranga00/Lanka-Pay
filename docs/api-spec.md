# Lanka Pay – API Specification

> **Draft** – will be verified against `docs/MASTER_PROMPT.md` section 16 once populated.

Base URL: `http://localhost:8080/api/v1`
Auth: Bearer JWT in `Authorization` header unless marked **Public**.
All request/response bodies are `application/json`.
All monetary amounts are `DECIMAL(15,2)` strings in LKR unless noted.

---

## Auth Errors (all endpoints)

| HTTP | Code | Meaning |
|---|---|---|
| 401 | `UNAUTHORIZED` | Missing or invalid JWT |
| 403 | `FORBIDDEN` | Valid JWT but insufficient role / object-level access denied |
| 429 | `RATE_LIMITED` | Too many requests |

---

## 1. Identity – `/auth`

### POST `/auth/register` — Public
Register a new user.

**Request**
```json
{
  "phone": "+94771234567",
  "fullName": "Kamal Perera",
  "nic": "199012345678"
}
```

**Response 202**
```json
{
  "userId": 1,
  "expiresAt": "2025-01-01T10:05:00Z"
}
```

**Errors**
| HTTP | Code | Reason |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Missing / invalid fields |
| 409 | `PHONE_ALREADY_REGISTERED` | Phone already in use |
| 409 | `NIC_ALREADY_REGISTERED` | NIC already in use |

---

### POST `/auth/verify-otp` — Public
Verify phone OTP after registration or login.

**Request**
```json
{ "userId": 1, "otp": "847291" }
```

**Response 200**
```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<token>",
  "expiresIn": 1800
}
```

**Errors**
| HTTP | Code | Reason |
|---|---|---|
| 400 | `OTP_INVALID` | Wrong OTP |
| 410 | `OTP_EXPIRED` | OTP TTL exceeded |
| 429 | `OTP_MAX_ATTEMPTS` | 5 failed attempts; account locked for 15 min |

---

### POST `/auth/login` — Public
Authenticate with phone + PIN.

**Request**
```json
{ "phone": "+94771234567", "pin": "123456" }
```

**Response 200** – same as verify-otp 200.

**Errors**
| HTTP | Code | Reason |
|---|---|---|
| 401 | `INVALID_CREDENTIALS` | Wrong phone or PIN |
| 403 | `ACCOUNT_FROZEN` | Account has been frozen |

---

### POST `/auth/refresh` — Public
Exchange refresh token for new access token.

**Request**
```json
{ "refreshToken": "<token>" }
```

**Response 200**
```json
{ "accessToken": "<jwt>", "expiresIn": 1800 }
```

---

### POST `/auth/logout` — Authenticated
Revoke refresh token.

**Request** – empty body
**Response 204** – No Content

---

### PATCH `/auth/change-pin` — Authenticated
Change user PIN.

**Request**
```json
{ "currentPin": "123456", "newPin": "654321" }
```

**Response 200**
```json
{ "message": "PIN updated successfully" }
```

---

## 2. Registry – `/registry`

### GET `/registry/lookup` — Authenticated
Look up a payee by phone number.

**Query Params:** `phone=+94771234567`

**Response 200**
```json
{
  "payeeId": 42,
  "displayName": "Kamal P.",
  "maskedPhone": "+9477***4567"
}
```

**Errors**
| HTTP | Code | Reason |
|---|---|---|
| 404 | `USER_NOT_FOUND` | No active, discoverable user with that phone |

---

### PATCH `/registry/discovery` — Authenticated
Enable or disable phone discovery.

**Request**
```json
{ "discoverable": false }
```

**Response 200**
```json
{ "discoverable": false }
```

---

## 3. Bank Integration – `/bank-accounts`

### POST `/bank-accounts` — Authenticated
Link a bank account.

**Request**
```json
{
  "bankCode": "SIMULATED_BOC",
  "accountNumber": "1234567890",
  "accountName": "Kamal Perera"
}
```

**Response 201**
```json
{
  "bankAccountId": 7,
  "bankCode": "SIMULATED_BOC",
  "maskedNumber": "****7890",
  "accountName": "Kamal Perera",
  "status": "ACTIVE",
  "simulated": true
}
```

**Errors**
| HTTP | Code | Reason |
|---|---|---|
| 422 | `BANK_VERIFICATION_FAILED` | Adapter could not verify account (SIMULATED) |
| 409 | `ACCOUNT_ALREADY_LINKED` | Duplicate account for this user |

---

### GET `/bank-accounts` — Authenticated
List user's linked bank accounts.

**Response 200**
```json
[
  {
    "bankAccountId": 7,
    "bankCode": "SIMULATED_BOC",
    "maskedNumber": "****7890",
    "isDefault": true,
    "status": "ACTIVE",
    "simulated": true
  }
]
```

---

### DELETE `/bank-accounts/{bankAccountId}` — Authenticated
Unlink a bank account. Object-level check: account must belong to requesting user.

**Response 204** – No Content

---

### PATCH `/bank-accounts/{bankAccountId}/default` — Authenticated
Set a linked account as the default.

**Response 200**
```json
{ "bankAccountId": 7, "isDefault": true }
```

---

## 4. Transfers – `/transfers`

### POST `/transfers` — Authenticated
Initiate a peer-to-peer transfer.

**Request**
```json
{
  "payeeId": 42,
  "amount": "1500.00",
  "currency": "LKR",
  "note": "Lunch split"
}
```

**Response 200**
```json
{
  "transferId": 99,
  "reference": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "amount": "1500.00",
  "currency": "LKR",
  "bankTxRef": "SIM-abc123",
  "simulated": true,
  "createdAt": "2025-01-01T10:30:00Z"
}
```

**Errors**
| HTTP | Code | Reason |
|---|---|---|
| 400 | `INVALID_AMOUNT` | Amount ≤ 0 or exceeds max |
| 422 | `DAILY_LIMIT_EXCEEDED` | Sender's daily cap reached |
| 422 | `BANK_DEBIT_FAILED` | Adapter debit rejected (SIMULATED) |
| 404 | `PAYEE_NOT_FOUND` | payeeId does not exist or not discoverable |

---

### GET `/transfers` — Authenticated
List the authenticated user's transfer history.

**Query Params:** `page=0&size=20&direction=SENT|RECEIVED`

**Response 200**
```json
{
  "content": [ { "transferId": 99, "reference": "...", "amount": "1500.00", "status": "COMPLETED", "simulated": true } ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

---

### GET `/transfers/{transferId}` — Authenticated
Get a single transfer. Object-level check: user must be sender or receiver.

**Response 200** – full transfer object as above.

---

## 5. QR – `/qr`

### POST `/qr/generate` — Authenticated
Generate a payment QR code.

**Request**
```json
{
  "amount": "500.00",
  "currency": "LKR",
  "note": "Table 5 bill"
}
```

**Response 200**
```json
{
  "qrId": "qr-uuid",
  "qrPayload": "LANKAPAY:qr-uuid:500.00:LKR:Table+5+bill",
  "expiresAt": "2025-01-01T10:45:00Z"
}
```

---

### POST `/qr/pay` — Authenticated
Pay via scanned QR payload.

**Request**
```json
{ "qrPayload": "LANKAPAY:qr-uuid:500.00:LKR:Table+5+bill" }
```

**Response 200** – transfer object (same as POST `/transfers` 200).

**Errors**
| HTTP | Code | Reason |
|---|---|---|
| 404 | `QR_NOT_FOUND` | Unknown QR payload |
| 410 | `QR_EXPIRED` | QR TTL exceeded |
| 409 | `QR_ALREADY_USED` | QR was already paid |
| 400 | `QR_SELF_PAYMENT` | Payer and merchant are the same user |

---

## 6. Admin – `/admin` (ROLE_ADMIN only)

### GET `/admin/users` — Admin
Search users.

**Query Params:** `phone`, `nic`, `status`, `page`, `size`

**Response 200** – paginated list of user summaries (no plaintext PII).

---

### GET `/admin/users/{userId}` — Admin
Get user detail.

**Response 200**
```json
{
  "userId": 1,
  "displayName": "Kamal P.",
  "maskedPhone": "+9477***4567",
  "maskedNic": "1990****",
  "status": "ACTIVE",
  "role": "USER",
  "createdAt": "2025-01-01T09:00:00Z"
}
```

---

### PATCH `/admin/users/{userId}/status` — Admin
Freeze or reactivate a user.

**Request**
```json
{ "status": "FROZEN", "reason": "Suspicious activity" }
```

**Response 200**
```json
{ "userId": 1, "status": "FROZEN" }
```

---

### GET `/admin/transfers` — Admin
Search all transfers.

**Query Params:** `userId`, `status`, `from`, `to`, `page`, `size`

**Response 200** – paginated transfer list.

---

### GET `/admin/audit-log` — Admin
Query the audit log.

**Query Params:** `actorId`, `eventType`, `from`, `to`, `page`, `size`

**Response 200** – paginated audit events.

---

## 7. Common Error Response Shape

```json
{
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "phone must be in E.164 format",
  "timestamp": "2025-01-01T10:00:00Z",
  "path": "/api/v1/auth/register"
}
```

Stack traces are **never** included in responses.

---

## 8. Cards & Simulated Vault – `/cards` and `/sim-vault`

> ⚠ **SIMULATED PROTOTYPE ONLY**: All card operations are test demonstrations. Never enter real card data.

### POST `/sim-vault/v1/tokenize`
Tokenize a test card. Validates against the seeded sandbox table.

**Request**
```json
{
  "pan": "9900XXXXXXXX0010",
  "expMonth": "12",
  "expYear": "27",
  "cvv": "111",
  "holderName": "Test Approved"
}
```

**Response 200**
```json
{
  "cardToken": "8ca8826a-2826-4cc3-af97-7e202d2b12fa",
  "last4": "0010",
  "expMonth": "12",
  "expYear": "27",
  "brandLabel": "TEST CARD",
  "simulated": true,
  "tokenExpiresAt": "2026-10-13T12:15:00Z"
}
```

---

### GET `/api/v1/cards`
List active, pending, and frozen cards owned by the authenticated user.

**Response 200**
```json
[
  {
    "id": 101,
    "last4": "0010",
    "expMonth": "12",
    "expYear": "27",
    "brandLabel": "TEST CARD",
    "nickname": "Daily Card",
    "cardKind": "LINKED",
    "status": "ACTIVE",
    "onlinePaymentsEnabled": true,
    "perPaymentLimitMinor": 5000000,
    "dailyLimitMinor": 20000000,
    "remainingDailyLimitMinor": 19000000,
    "simulated": true
  }
]
```

---

### POST `/api/v1/cards`
Link an existing tokenized card with user consent and step-up PIN. Initiates bank OTP challenge (`PENDING_VERIFICATION`).

**Headers:** `X-Step-Up-Pin: 123456` (or in body)

**Request**
```json
{
  "cardToken": "8ca8826a-2826-4cc3-af97-7e202d2b12fa",
  "nickname": "Daily Card",
  "linkedBankAccountId": 1,
  "consentGiven": true
}
```

**Response 201**
```json
{
  "id": 101,
  "last4": "0010",
  "status": "PENDING_VERIFICATION",
  "challengeRef": "chal-778899"
}
```

---

### POST `/api/v1/cards/{id}/verify`
Complete the bank OTP ownership challenge to transition card from `PENDING_VERIFICATION` to `ACTIVE`.

**Request**
```json
{
  "otp": "112233"
}
```

**Response 200**
```json
{
  "id": 101,
  "status": "ACTIVE"
}
```

---

### POST `/api/v1/cards/virtual`
Issue an instant virtual card tied to an active bank account.

**Headers:** `X-Step-Up-Pin: 123456`

**Request**
```json
{
  "linkedBankAccountId": 1,
  "nickname": "Online Shopping"
}
```

**Response 201**
```json
{
  "id": 102,
  "last4": "0010",
  "cardKind": "VIRTUAL",
  "status": "ACTIVE"
}
```

---

### POST `/api/v1/cards/{id}/freeze`
Freeze a card immediately to stop all future debits.

**Response 200**
```json
{
  "id": 101,
  "status": "FROZEN"
}
```

---

### POST `/api/v1/cards/{id}/unfreeze`
Unfreeze a card back to `ACTIVE`. Requires step-up PIN in `X-Step-Up-Pin` header.

**Headers:** `X-Step-Up-Pin: 123456`

**Response 200**
```json
{
  "id": 101,
  "status": "ACTIVE"
}
```

---

### POST `/api/v1/cards/{id}/reveal`
Reveal virtual card PAN, CVV, and full expiry. Strictly limited to `VIRTUAL` cards.
Protected by step-up PIN and rate limited to 3 reveals per hour. Returns `Cache-Control: no-store`.

**Headers:** `X-Step-Up-Pin: 123456`

**Response 200**
```json
{
  "cardId": 102,
  "last4": "0010",
  "pan": "9900XXXXXXXX0010",
  "cvv": "111",
  "expMonth": "12",
  "expYear": "27",
  "brandLabel": "TEST CARD",
  "expiresInSeconds": 30,
  "simulated": true
}
```

---

### PATCH `/api/v1/cards/{id}`
Update nickname, limits, or toggle online payments. Requires step-up PIN.

**Headers:** `X-Step-Up-Pin: 123456`

**Request**
```json
{
  "nickname": "Personal Card",
  "perPaymentLimitMinor": 2500000,
  "dailyLimitMinor": 10000000,
  "onlinePaymentsEnabled": false
}
```

**Response 200** – updated card object.

---

### DELETE `/api/v1/cards/{id}`
Soft-delete / remove card (`REMOVED` status). Requires step-up PIN in `X-Step-Up-Pin` header.

**Headers:** `X-Step-Up-Pin: 123456`

**Response 204 No Content**

