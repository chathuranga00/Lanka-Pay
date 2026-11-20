# LankaPay Super-App – REST API Specification

> **Base URL**: `http://localhost:8080/api/v1`  
> **Auth**: Bearer JWT in `Authorization` header unless designated `Public`  
> **Monetary Standard**: All monetary values are strictly represented in **integer minor units** (`amountMinor`, `feeMinor`, `totalMinor` as 64-bit integers, 1 LKR = 100 minor units).

---

## 1. Global Headers & Common Error Codes

### 1.1 Standard Request Headers
| Header | Type | Description |
|---|---|---|
| `Authorization` | `String` | `Bearer <access-token>` (Required for non-public endpoints) |
| `Idempotency-Key` | `UUID` | Required for all state-mutating payment and creation endpoints |
| `X-Step-Up-Pin` | `String` | Required 6-digit PIN for high-value actions (card unfreeze, payments > threshold) |
| `X-Correlation-Id` | `UUID` | Optional client trace identifier (propagated to audit logs) |

### 1.2 Global Error Responses
| HTTP | Error Code | Description |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Request payload failed schema or business constraint checks |
| 401 | `UNAUTHORIZED` | Missing, malformed, or expired JWT token |
| 403 | `FORBIDDEN` | Insufficient permissions or IDOR object access denied |
| 403 | `FEATURE_DISABLED` | Server-side feature flag is disabled (`{"error": "FEATURE_DISABLED", "feature": "bills"}`) |
| 404 | `NOT_FOUND` | Referenced entity was not found |
| 409 | `IDEMPOTENCY_CONFLICT` | Replay with different payload under an active idempotency key |
| 422 | `INSUFFICIENT_FUNDS` | Debited account or card does not possess required balance |
| 429 | `RATE_LIMIT_EXCEEDED`| Request rate limit exceeded; includes cooldown guidance |
| 500 | `INTERNAL_ERROR` | Unexpected server condition (error details redacted) |

---

## 2. Authentication & Identity (`/auth`)

### POST `/auth/register` (Public)
Initiates user registration.
```json
// Request
{
  "phone": "+94771234567",
  "fullName": "Kamal Perera",
  "nic": "199012345678",
  "pin": "123456"
}

// Response 202 Accepted
{
  "userId": 1,
  "status": "PENDING_OTP",
  "otpExpiresAt": "2026-10-14T10:05:00Z"
}
```

### POST `/auth/verify-otp` (Public)
Validates registration or login OTP.
```json
// Request
{ "userId": 1, "otp": "123456" }

// Response 200 OK
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "d748f93e...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

### POST `/auth/login` (Public)
Authenticates existing user with phone and PIN.
```json
// Request
{ "phone": "+94771234567", "pin": "123456" }

// Response 200 OK
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "d748f93e...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

---

## 3. Accounts & Bank Integration (`/accounts`)

### GET `/accounts`
Lists all linked accounts owned by the authenticated caller.
```json
// Response 200 OK
[
  {
    "id": 1,
    "institutionCode": "BOC",
    "bankName": "Bank of Ceylon",
    "maskedAccountNumber": "•••• 5678",
    "label": "Primary Account",
    "isDefault": true,
    "balanceMinor": 12545000,
    "currency": "LKR",
    "status": "ACTIVE"
  }
]
```

### POST `/accounts/link`
Links a simulated bank account with consent.
```json
// Request
{
  "institutionCode": "BOC",
  "accountNumber": "100234567890",
  "accountName": "Kamal Perera",
  "label": "Salary Account",
  "consentGiven": true
}
```

---

## 4. Cards & SimVault (`/cards`)

### GET `/cards`
Returns active tokenized and virtual cards for the caller.
```json
// Response 200 OK
[
  {
    "id": 101,
    "cardToken": "tok_sim_0010",
    "last4": "0010",
    "brandLabel": "TEST CARD",
    "nickname": "Virtual Daily Card",
    "cardKind": "VIRTUAL",
    "status": "ACTIVE",
    "perPaymentLimitMinor": 2500000,
    "dailyLimitMinor": 10000000,
    "simulated": true
  }
]
```

### POST `/cards/virtual`
Issues a new instant virtual card linked to a bank account.
```json
// Request
{
  "linkedBankAccountId": 1,
  "nickname": "Shopping Card",
  "perPaymentLimitMinor": 5000000
}
```

### POST `/cards/{id}/freeze` / `/cards/{id}/unfreeze`
Freezes card instantly or unfreezes with step-up verification.
`X-Step-Up-Pin: 123456` required on unfreeze.

---

## 5. Transfers & LankaPay-ID (`/transfers`)

### POST `/transfers`
Executes an instant P2P or inter-bank transfer.
```json
// Headers: Idempotency-Key: c9d8e7b6-1234-4567-8901-abcdef123456
// Request
{
  "receiverPhone": "+94772345678",
  "amountMinor": 250000,
  "currency": "LKR",
  "description": "Lunch split",
  "fundingType": "ACCOUNT",
  "fundingSourceId": 1
}

// Response 200 OK
{
  "transferId": 501,
  "reference": "TX-501-ABC",
  "status": "COMPLETED",
  "amountMinor": 250000,
  "currency": "LKR",
  "receiverDisplayName": "Nimali Silva",
  "completedAt": "2026-10-14T01:10:00Z",
  "simulated": true
}
```

---

## 6. Home Screen Aggregator (`/home`)

### GET `/home`
High-performance resilient dashboard aggregation endpoint.
Guaranteed < 500ms response SLA with section failure isolation.

```json
// Response 200 OK
{
  "greeting": {
    "status": "OK",
    "data": { "fullName": "Kamal Perera", "initial": "K", "salutation": "Good morning, Kamal" }
  },
  "wallet": {
    "status": "OK",
    "data": {
      "primaryBalanceMinor": 12545000,
      "primaryBalanceLkr": "125,450.00",
      "currency": "LKR",
      "isSimulated": true,
      "defaultAccount": {
        "id": 1,
        "bankName": "Bank of Ceylon",
        "maskedAccountNumber": "•••• 5678"
      }
    }
  },
  "spendSummary": {
    "status": "OK",
    "data": {
      "totalSpentMinor": 348000,
      "totalSpentLkr": "3,480.00",
      "budgetMinor": 5000000,
      "budgetUsedFraction": 0.0696,
      "month": "October",
      "hasTransactions": true
    }
  },
  "rewardsPoints": {
    "status": "OK",
    "data": { "points": "1,250 pts", "hasNewOffers": true }
  },
  "nudge": {
    "status": "OK",
    "data": {
      "nudgeId": "security_tip",
      "type": "SECURITY_TIP",
      "message": "Never share your PIN, OTP, or password with anyone.",
      "actionLabel": "Security Settings",
      "actionRoute": "/settings/security"
    }
  }
}
```

---

## 7. Utility Bills (`/bills`)

*Guarded by `@FeatureToggle(FeatureName.BILLS)`*

### GET `/bills/billers`
Lists utility and telco billers with fee schedules.
```json
// Response 200 OK
[
  {
    "id": "SIM_ELEC_01",
    "name": "Simulated Electricity Board",
    "category": "ELECTRICITY",
    "convenienceFeeMinor": 3000
  }
]
```

### POST `/bills/pay`
Executes utility payment with fee calculation.
```json
// Headers: Idempotency-Key: uuid, X-Step-Up-Pin: 123456
// Request
{
  "billerId": "SIM_ELEC_01",
  "accountReference": "1009876543",
  "amountMinor": 345000,
  "fundingType": "CARD",
  "fundingSourceId": 101
}

// Response 200 OK
{
  "receiptId": "REC-98765",
  "billerName": "Simulated Electricity Board",
  "amountMinor": 345000,
  "convenienceFeeMinor": 3000,
  "totalMinor": 348000,
  "status": "COMPLETED",
  "timestamp": "2026-10-14T01:15:00Z"
}
```

---

## 8. Standing Orders & Scheduled Payments (`/scheduled`)

*Guarded by `@FeatureToggle(FeatureName.SCHEDULED)`*

### POST `/scheduled`
Creates a recurring monthly or weekly scheduled payment.
```json
// Request
{
  "payeeType": "BILLER",
  "payeeTarget": "SIM_ELEC_01",
  "amountMinor": 350000,
  "frequency": "MONTHLY",
  "startDate": "2026-11-01"
}
```

### POST `/scheduled/{id}/pause` / `/scheduled/{id}/resume`
Toggles active schedule status.

---

## 9. Bill Splitting (`/split`)

*Guarded by `@FeatureToggle(FeatureName.SPLIT)`*

### POST `/split`
Creates an equal or custom group bill split.
```json
// Request
{
  "title": "Dinner at Cinnamon Leaf",
  "totalAmountMinor": 600000,
  "participants": [
    { "phone": "+94772345678", "name": "Nimali Silva", "amountMinor": 200000 },
    { "phone": "+94773456789", "name": "Kasun Perera", "amountMinor": 200000 }
  ]
}
```

### POST `/split/{id}/remind`
Sends rate-limited reminder SMS/push notification to pending participants.
Enforces a 60-second cooldown per split.

---

## 10. Loyalty Rewards (`/rewards`)

*Guarded by `@FeatureToggle(FeatureName.REWARDS)`*

### GET `/rewards/balance`
Returns point balance, tier level, and expiry.
```json
// Response 200 OK
{
  "points": 1250,
  "tier": "SILVER",
  "expiringPoints": 150,
  "expiryDate": "2026-12-31"
}
```

### POST `/rewards/redeem`
Redeems loyalty points for a merchant discount voucher.
```json
// Request
{ "offerId": "off_fuel_100", "pointsCost": 500 }

// Response 200 OK
{
  "voucherCode": "LANKAPAY-FUEL-500",
  "discountMinor": 50000,
  "merchantId": "merch_fuel_1",
  "remainingPoints": 750
}
```

---

## 11. City & In-Store Discovery (`/city`)

*Guarded by `@FeatureToggle(FeatureName.CITY)`*

### GET `/city/merchants`
Returns local merchant catalog with active offers.
Filterable by `category` (`food`, `groceries`, `fuel`) and `query`.

### POST `/city/resolve-qr`
Parses LankaQR payload string and returns merchant identity and transaction metadata.

---

## 12. Support & Customer Service (`/support`)

*Guarded by `@FeatureToggle(FeatureName.SUPPORT)`*

### POST `/support/conversations/{id}/messages`
Sends user query to FAQ bot or live support agent. Output is strictly HTML-sanitized.

### POST `/support/conversations/{id}/request-agent`
Transfers automated conversation session to human operator queue.

---

## 13. Financial Literacy Stories (`/stories`)

*Guarded by `@FeatureToggle(FeatureName.STORIES)`*

### GET `/stories`
Returns active educational stories with whitelisted app routes.
```json
// Response 200 OK
[
  {
    "id": "story-1",
    "title": "Protect Your Security PIN",
    "caption": "Best practices for card safety",
    "seen": false,
    "pages": [
      {
        "text": "Never disclose your PIN to any third party.",
        "actionLabel": "Security Settings",
        "deepLink": "/settings/security"
      }
    ]
  }
]
```

### POST `/stories/{id}/seen`
Records story view state for user.

---

## 14. Server-Side Feature Toggles (`/features`, `/admin/features`)

### GET `/features` (Public)
Lists all feature toggle states for mobile client hydration.
```json
// Response 200 OK
{
  "bills": true,
  "scheduled": true,
  "split": true,
  "rewards": true,
  "city": true,
  "support": true,
  "stories": true,
  "family": false,
  "investments": false
}
```

### PUT `/admin/features/{featureName}` (Admin Only)
Enables or disables feature toggle with audit attribution.
```json
// Request
{ "enabled": false, "reason": "Scheduled maintenance" }
```
When disabled, subsequent API requests to the module return `HTTP 403 FEATURE_DISABLED`.
