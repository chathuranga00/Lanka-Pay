# LankaPay Data Model Specification

> **SIMULATED PROTOTYPE ONLY**: All card records represent simulated test data. No real card numbers or CVVs are ever stored.

---

## 1. Cards Collection (`cards`)

Stores virtual and linked payment cards. Persisted in Google Cloud Firestore (`cards` collection) and queried by `userId`.

### Schema & Fields

| Field Name | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `Long` | Primary Key, Unique | Auto-generated 64-bit identifier |
| `userId` | `Long` | Required, Indexed | Foreign key to `idn_users.id` (tenant owner) |
| `linkedBankAccountId`| `Long` | Optional, Indexed | Foreign key to `bnk_bank_accounts.id` (source funding account) |
| `cardToken` | `String` | Required, Unique | Opaque surrogate token issued by simulated vault |
| `last4` | `String` | Required (4 digits) | Safe display digits (e.g. `"0010"`) |
| `expMonth` | `Integer` | 1–12 | Expiration month |
| `expYear` | `Integer` | 2-digit / 4-digit | Expiration year |
| `brandLabel` | `String` | Required | Always `"TEST CARD"` in prototype |
| `nickname` | `String` | Max 50 chars | User-friendly label (e.g. `"Online Subscriptions"`) |
| `cardKind` | `String` | Enum | `LINKED` (simulated external card) or `VIRTUAL` (issued instant card) |
| `status` | `String` | Enum | `PENDING_VERIFICATION`, `ACTIVE`, `FROZEN`, `REMOVED` |
| `onlinePaymentsEnabled` | `Boolean`| Default `true` | E-commerce transaction toggle |
| `perPaymentLimitMinor` | `Long` | Minor units (LKR) | Max minor units per single payment (default 5,000,000 = 50,000.00 LKR) |
| `dailyLimitMinor` | `Long` | Minor units (LKR) | Max aggregate daily spend (default 20,000,000 = 200,000.00 LKR) |
| `challengeRef` | `String` | Optional (10 min TTL)| Transient bank OTP challenge reference |
| `simulated` | `Boolean`| Always `true` | Demonstration prototype flag |
| `createdAt` | `Timestamp` | Required | Document creation timestamp |
| `updatedAt` | `Timestamp` | Required | Last update timestamp |
| `verifiedAt` | `Timestamp` | Optional | Timestamp when bank OTP verification completed |
| `removedAt` | `Timestamp` | Optional | Timestamp when user removed card (soft-delete) |
| `version` | `Long` | Required | Optimistic locking counter |

### Explicitly Excluded (Prohibited from Storage)
- `pan`: Primary Account Number is NEVER persisted.
- `cvv`: Card Verification Value is NEVER persisted.
- `pin`: Card PIN or user PIN is NEVER stored in cleartext.

---

## 2. Card Consents Subcollection (`cardConsents`)

Records explicit user consent prior to linking external card tokens.

| Field Name | Type | Description |
| :--- | :--- | :--- |
| `id` | `String` | Document ID (`{userId}_{cardId}`) |
| `userId` | `Long` | User granting consent |
| `cardId` | `Long` | Associated card record |
| `consentType` | `String` | Type of consent (`"LINK_CARD"`, `"TERMS_AND_CONDITIONS"`) |
| `granted` | `Boolean` | `true` |
| `clientIp` | `String` | IP address at the moment of consent |
| `timestamp` | `Timestamp` | Audit timestamp |

---

## 3. Transactions Collection (`transactions`) – Card Extensions

Extends core transfer transaction documents with card funding fields.

| Field Name | Type | Description |
| :--- | :--- | :--- |
| `fundingType` | `String` | `ACCOUNT` or `CARD` |
| `cardId` | `Long` | Foreign key to `cards.id` when funded by card |
| `cardLast4` | `String` | Truncated last 4 digits (e.g. `"0010"`) |
| `cardKind` | `String` | `LINKED` or `VIRTUAL` |
| `fee` | `BigDecimal` | Simulated card interchange fee (1.5%) |
| `totalAmount` | `BigDecimal` | Principal amount plus fee |
| `providerReference`| `String` | Auth code returned by card provider (e.g. `"AUTH-0010"`) |
