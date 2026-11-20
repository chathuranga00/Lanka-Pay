# LankaPay Card Security Policy & Data Lifecycle

> **IMPORTANT WARNING**  
> All payment card functionality in LankaPay is strictly a **SIMULATED UNIVERSITY DEMONSTRATION PROTOTYPE**.  
> **NEVER enter a real credit or debit card number.** All test transactions are simulated with no connection to actual payment schemes (Visa, Mastercard, etc.) and no real funds are ever transferred.

---

## 1. Executive Summary & PCI-DSS Scope

The LankaPay card subsystem is engineered according to **PCI-DSS v4.0** principles (specifically Requirement 3: Protect Stored Cardholder Data, Requirement 4: Protect Cardholder Data with Strong Cryptography during Transmission, Requirement 9: Restrict Physical and Logical Access, and Requirement 10: Log and Monitor All Access).

In accordance with minimal cardholder data environment (CDE) scoping:
- The core application backend, database, Firestore collections, message queues, and analytics services **NEVER** handle, store, or process raw Primary Account Numbers (PANs), CVVs, or cardholder PINs.
- Tokenization separates the rest of the application entirely from cardholder data through opaque tokens (`cardToken`).

---

## 2. Card Data Classification

| Data Element | Stored? | Where Stored | Format / Protection | Permitted Uses |
| :--- | :---: | :---: | :---: | :---: |
| **Primary Account Number (PAN)** | **NEVER** | Never stored in persistent DB | Transient in-memory tokenization only; zero-filled after token generation | Tokenization only |
| **Card Verification Value (CVV / CVC)** | **NEVER** | Never stored anywhere | Ephemeral in-memory verification only | Tokenization / Auth check |
| **Cardholder PIN / Step-Up PIN** | **NEVER** | Never on card record | Bcrypt-hashed (`$2a$12$...`) on user identity entity only | Step-up authorization |
| **Card Token (`cardToken`)** | **YES** | `cards` collection | Opaque UUID string bound to `userId` | Recurring payments & funding |
| **Masked PAN (`last4`)** | **YES** | `cards`, `transactions` | Truncated last 4 digits (e.g. `0010`) | Display & receipt generation |
| **Expiration Date (`expMM/expYY`)** | **YES** | `cards` collection | Two-digit month and year strings | Client display & pre-flight expiry check |
| **Brand Label** | **YES** | `cards` collection | "TEST CARD" | UI display |
| **Cardholder Name / Nickname** | **YES** | `cards` collection | Plaintext string (max 50 chars) | User identification |
| **Spending Limits** | **YES** | `cards` collection | Integer minor units in LKR | Limit enforcement |
| **Card Status** | **YES** | `cards` collection | Enum (`PENDING_VERIFICATION`, `ACTIVE`, `FROZEN`, `REMOVED`) | State machine lifecycle |
| **Challenge Reference (`challengeRef`)** | **YES** | `cards` collection | Transient UUID (10 min TTL) | Bank OTP challenge linking |

---

## 3. What is Stored vs. What is Never Stored

### What is Stored (Firestore `cards` Collection)
- `id`: Internal unique 64-bit identifier.
- `userId`: Owner identifier enforcing tenant isolation (IDOR protection).
- `linkedBankAccountId`: Optional link to underlying funding bank account.
- `cardToken`: Opaque surrogate key representing the tokenized card.
- `last4`: Truncated last four digits for display.
- `expMonth` and `expYear`: Card expiration date.
- `brandLabel`: "TEST CARD".
- `nickname`: User-provided card label.
- `cardKind`: `LINKED` or `VIRTUAL`.
- `status`: Card lifecycle state.
- `onlinePaymentsEnabled`: Boolean toggle controlling e-commerce / web payments.
- `perPaymentLimitMinor`: Maximum allowed transaction limit in minor units (LKR).
- `dailyLimitMinor`: Maximum aggregate daily transaction limit in minor units (LKR).
- `createdAt`, `updatedAt`, `verifiedAt`, `removedAt`: Audit timestamps.
- `version`: Optimistic locking version integer.
- `challengeRef`: Ephemeral ownership challenge token.
- `simulated`: Boolean flag (always `true`).

### What is NEVER Stored
1. **Full 13–19 Digit Primary Account Numbers (PANs)**:
   - Not in database tables.
   - Not in Firestore documents.
   - Not in Redis / cache layers.
   - Not in backend application logs (filtered by `CardFieldMaskingConverter`).
   - Not in analytics payloads.
   - Not in serialized DTO `toString()` representations (redacted automatically).
2. **Card Verification Values (CVVs / CVCs)**:
   - Prohibited from storage by PCI-DSS Requirement 3.2. Discarded immediately after tokenization or authorization check.
3. **Cardholder PINs in Cleartext**:
   - Transmitted exclusively over HTTPS with TLS 1.3.
   - Verified via one-way salted BCrypt hash against the user account.
4. **Permanent Revealed Virtual Card Details**:
   - The reveal endpoint returns virtual PAN/CVV with `Cache-Control: no-store, no-cache, must-revalidate`.
   - In-memory mobile state wipes card data after 30 seconds or when the application is sent to the background.

---

## 4. Who Sees Card Data (Access Control & Data Flow)

### 1. User / Cardholder
- During **card addition**: The user enters the card number and CVV into the client interface. The client sends it directly to the tokenization endpoint over TLS.
- During **virtual card reveal**: The authenticated user passes biometric or step-up PIN verification. The client receives the details over TLS and displays them with an automatic 30-second countdown. The details are not cached or stored on the device filesystem.

### 2. Simulated Vault Service (`/sim-vault`)
- Receives the raw PAN, CVV, expiry, and holder name.
- Validates the test card against the seeded sandbox table using SHA-256 PAN hashing.
- Produces an opaque `cardToken` and returns only `last4`, `brandLabel`, and expiry.
- Overwrites in-memory request buffers (`"0000000000000000"`) prior to garbage collection.

### 3. Core LankaPay Backend Services
- **`CardService`**: Consumes only `cardToken` and `last4`. Operates completely out of scope of raw cardholder data.
- **`TransferService`**: Processes card-funded transfers by delegating authorization to the provider via `cardToken`.
- **`AuditService`**: Logs only masked references: `last4` and `cardKind`.

### 4. Admin & Support Personnels
- Admin dashboard screens and API endpoints display only masked numbers (e.g. `•••• •••• •••• 0010`).
- No database administrators, support agents, or engineers can access raw PANs or CVVs.

---

## 5. Security Scanning & Defense in Depth

1. **Automated CI PII / Secret Scanner (`scripts/scan-luhn-cards.py`)**:
   - Scans every pull request and commit for any 13–19 digit number satisfying the Luhn checksum formula (MOD 10).
   - Strictly fails the CI build if any Luhn-valid card number appears anywhere in the codebase outside of `docs/test-cards.md`.
2. **Gitleaks Rule Enforcement (`.gitleaks.toml`)**:
   - Proactively catches payment card patterns, private keys, and credential leaks.
3. **Logback Custom Card Redaction (`CardFieldMaskingConverter`)**:
   - Automatically sanitizes JSON fields matching `pan`, `cardNumber`, `cvv`, `expiry`, and raw card runs in all application log appenders before write.
4. **End-to-End Log & DB Scan Integration Test (`CardDemoE2ETest`)**:
   - Executes the complete card lifecycle (add, challenge OTP, purchase, freeze, decline, unfreeze, remove).
   - Collects all captured application logs, in-memory records, and persistent stores to assert zero leakage of card numbers and CVVs.
