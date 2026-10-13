# LankaPay Super-App – Data Model Specification

> **Status**: Comprehensive Reference  
> **Storage Target**: Google Cloud Firestore & In-Memory Resilient Store  
> **Simulation Disclaimer**: All financial data, bank accounts, cards, and balances represent simulated test accounts. No real fiat money is moved.

---

## 1. Core Invariants & Currency Representation

### 1.1 The Integer Minor Units Rule (`amountMinor`)
To guarantee mathematical correctness and eliminate floating-point rounding errors (e.g., IEEE-754 inaccuracies):
- **All fiat amounts must be stored as 64-bit signed integers (`Long`) representing minor currency units.**
- In Sri Lanka Rupee (LKR), 1 LKR = 100 cents (minor units).
- Example: `3,450.00 LKR` is strictly stored as `345000` minor units.
- Convenience fees and totals also adhere to minor units: `30.00 LKR` fee = `3000` minor units; Total = `348000` minor units.
- Display representations (e.g., `"3,450.00"`) are generated only at presentation time.

### 1.2 Separation of Fiat Money vs. Loyalty Points
- **Fiat Balances**: Held in linked bank accounts (`bankAccounts`) and cards (`cards`), quantified in `amountMinor`.
- **Loyalty Points**: Held in the rewards ledger (`pointsLedger`), quantified in whole integer points (`points`).
- Points are earned **only upon confirmed transaction `COMPLETED` state**.
- If a transaction is reversed or refunded, earned points are immediately rolled back via a negative ledger entry (`REVERSAL`).
- Points are never mixed into fiat ledgers. Points can be redeemed for promotional vouchers or partner rebates, never converted into withdrawable bank currency.

### 1.3 Append-Only Accounting
- Core financial tables (`transfers`, `pointsLedger`, `scheduledExecutions`, `auditLogs`) are **append-only**.
- State transitions update status and timestamp (`updatedAt`), but previous transaction records are never deleted or rewritten.

---

## 2. Collections & Schemas

### 2.1 Identity & User Profiles (`users`)
Stores registered super-app users and authentication credentials.

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Primary user identifier (64-bit sequence/hash) |
| `phone` | `String` | Normalized E.164 phone number (`+9477XXXXXXX`) |
| `fullName` | `String` | Full name as registered with National ID |
| `nic` | `String` | Sri Lankan National Identity Card (`199012345678` or `901234567V`) |
| `pinHash` | `String` | BCrypt hash of 6-digit transaction PIN |
| `passwordHash` | `String` | BCrypt hash of login password |
| `status` | `String` | `ACTIVE`, `FROZEN`, `LOCKED` |
| `role` | `String` | `CUSTOMER`, `ADMIN` |
| `discoverableByPhone` | `Boolean` | LankaPay-ID lookup directory toggle |
| `failedLoginAttempts` | `Integer` | Consecutive failed attempts counter |
| `lockedUntil` | `Timestamp` | Cooldown expiry timestamp if locked |
| `createdAt` | `Timestamp` | Account creation timestamp |

---

### 2.2 Linked Bank Accounts (`bankAccounts`)
Stores accounts linked via the `BankAdapter` SPI.

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Unique account identifier |
| `userId` | `Long` | Foreign key to `users.id` |
| `institutionCode` | `String` | Bank code (`BOC`, `COMB`, `HNB`, `SIM_BANK_A`) |
| `accountNumberEncrypted` | `String` | AES-256-GCM encrypted account number |
| `maskedAccountNumber` | `String` | Safe display representation (`•••• 5678`) |
| `accountName` | `String` | Account title returned by core banking |
| `label` | `String` | User nickname (e.g. `"Primary Salary"`) |
| `isDefault` | `Boolean` | Default debit source indicator |
| `simulatedBalanceMinor`| `Long` | Simulated balance in minor units (e.g., `12545000` = 125,450.00 LKR) |
| `receivingEnabled` | `Boolean` | Eligible to receive inbound LankaPay-ID transfers |
| `status` | `String` | `ACTIVE`, `FROZEN`, `UNLINKED` |

---

### 2.3 Cards & Token Vault (`cards`)
Stores virtual cards and tokenized payment cards (PCI-DSS compliant).

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Unique card identifier |
| `userId` | `Long` | Foreign key to `users.id` |
| `cardToken` | `String` | Opaque surrogate token issued by SimVault |
| `last4` | `String` | Safe display 4 digits (`"0010"`) |
| `brandLabel` | `String` | `"TEST CARD"` |
| `nickname` | `String` | Label (e.g. `"Virtual Shopping Card"`) |
| `cardKind` | `String` | `VIRTUAL` or `LINKED` |
| `status` | `String` | `ACTIVE`, `FROZEN`, `REMOVED` |
| `perPaymentLimitMinor` | `Long` | Max minor units per single payment |
| `dailyLimitMinor` | `Long` | Max aggregate spend per calendar day |
| `linkedBankAccountId` | `Long` | Source funding account identifier |
| `simulated` | `Boolean` | Always `true` |

---

### 2.4 Fund Transfers (`transfers`)
Append-only log of P2P, LankaPay-ID, and QR fund movements.

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Unique transfer identifier |
| `reference` | `String` | Unique external tracking reference (`TX-XXXXX`) |
| `idempotencyKey` | `String` | Client-provided UUID to prevent double-charges |
| `senderUserId` | `Long` | User initiating payment |
| `receiverUserId` | `Long` | User receiving payment |
| `receiverPhone` | `String` | Recipient mobile number |
| `receiverDisplayName` | `String` | Masked recipient name |
| `amountMinor` | `Long` | Principal transfer amount in minor units |
| `currency` | `String` | Currency code (`"LKR"`) |
| `fundingType` | `String` | `ACCOUNT` or `CARD` |
| `fundingSourceId` | `Long` | Account ID or Card ID used for funding |
| `status` | `String` | `PENDING`, `COMPLETED`, `FAILED`, `REVERSED` |
| `failureReason` | `String` | Error code if failed (`INSUFFICIENT_FUNDS`, etc.) |
| `createdAt` | `Timestamp` | Transaction initiation time |
| `completedAt` | `Timestamp` | Final confirmation time |

---

### 2.5 Utility Billers & Payments (`billers`, `billPayments`)
Manages utility bills catalog and payment receipts.

| Collection | Key Fields | Invariants / Notes |
|---|---|---|
| `billers` | `id`, `name`, `category` (`ELECTRICITY`, `WATER`, `TELCO`), `convenienceFeeMinor` | Standard biller definitions |
| `billPayments` | `id`, `userId`, `billerId`, `accountRef`, `amountMinor`, `feeMinor`, `totalMinor`, `status`, `receiptRef` | `totalMinor == amountMinor + feeMinor`. Idempotent by `idempotencyKey`. |

---

### 2.6 Standing Orders & Schedules (`scheduledPayments`)
Manages recurring monthly or weekly scheduled payments.

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Unique schedule identifier |
| `userId` | `Long` | Schedule owner |
| `payeeType` | `String` | `BILLER`, `CONTACT`, `ACCOUNT` |
| `payeeTarget` | `String` | Biller ID, Phone number, or Account number |
| `amountMinor` | `Long` | Amount per occurrence in minor units |
| `frequency` | `String` | `MONTHLY`, `WEEKLY`, `ONCE` |
| `nextRunDate` | `LocalDate`| Scheduled date of next execution |
| `status` | `String` | `ACTIVE`, `PAUSED`, `COMPLETED`, `CANCELLED` |
| `executionCount` | `Integer` | Number of executed runs |

---

### 2.7 Group Bill Splitting (`splitBills`)
Manages shared dining, taxi, and event bill splits.

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Unique split bill identifier |
| `ownerUserId` | `Long` | User creating the split |
| `title` | `String` | Description (e.g. `"Dinner at Cinnamon Leaf"`) |
| `totalAmountMinor` | `Long` | Aggregate bill in minor units |
| `status` | `String` | `PENDING`, `SETTLED`, `CANCELLED` |
| `participants` | `List<Map>`| Items with `phone`, `amountMinor`, `status` (`PENDING`, `PAID`) |
| `lastRemindedAt` | `Timestamp` | Timestamp enforcing 60s reminder rate limit |

---

### 2.8 Loyalty Rewards Ledger (`pointsLedger`)
Points ledger entries tracking accrual and voucher redemption.

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Ledger entry sequence identifier |
| `userId` | `Long` | Account owner |
| `points` | `Integer` | Signed point delta (`+150` on spend, `-500` on redeem) |
| `entryType` | `String` | `EARN`, `REVERSAL`, `REDEEM`, `PROMO` |
| `referenceId` | `String` | Transfer reference or voucher code |
| `balanceAfter` | `Integer` | Cumulative points balance after transaction |
| `createdAt` | `Timestamp` | Ledger entry timestamp |

---

### 2.9 City & LankaQR Merchants (`merchants`)
In-store merchants with dynamic/static LankaQR specifications.

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Merchant identifier (`merch_1`, `merch_2`) |
| `name` | `String` | Registered trading name |
| `category` | `String` | `FOOD`, `GROCERIES`, `FUEL`, `RETAIL` |
| `lankaQrPayload` | `String` | EMVCo / LankaQR compliant payload string |
| `activeOffers` | `List<Map>`| Promotional discount offers and codes |

---

### 2.10 Customer Support Conversations (`supportConversations`)
Customer care interactions, AI FAQ answers, and human handoff.

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Conversation session ID |
| `userId` | `Long` | Inquiring customer |
| `status` | `String` | `OPEN`, `WAITING_FOR_AGENT`, `RESOLVED` |
| `messages` | `List<Map>`| `sender` (`USER`, `BOT`, `AGENT`), `sanitizedText`, `timestamp` |

---

### 2.11 Financial Literacy Stories (`stories`)
Educational stories on budgeting, security, and neo-banking tips.

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Story unique identifier (`story-1`) |
| `title` | `String` | Story title |
| `caption` | `String` | Short subtitle |
| `gradientOrImage` | `String` | Gradient CSS/token string |
| `priority` | `Integer` | Display priority ordering |
| `whitelistedRoutes`| `List<String>`| Safe in-app routes allowed for action button (e.g. `['/accounts/add', '/settings/security']`) |

---

### 2.12 Server Feature Flags (`featureFlags`)
Dynamic runtime toggles controlling backend modules.

| Field | Type | Description |
|---|---|---|
| `featureName` | `String` | Unique flag key (`bills`, `scheduled`, `split`, `rewards`, `city`, etc.) |
| `enabled` | `Boolean` | Current enablement status |
| `description` | `String` | Human-readable module capability |
| `updatedBy` | `String` | Admin username who modified flag |
| `updatedAt` | `Timestamp` | Last modification timestamp |
