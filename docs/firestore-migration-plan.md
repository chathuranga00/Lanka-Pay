# LankaPay ID — Architecture Migration Plan: MySQL/JPA to Google Cloud Firestore

**Document Version:** 1.0.0  
**Status:** Architectural Assessment & Migration Specification  
**Scope:** `/backend` Java 21 / Spring Boot 3 Modular Monolith  
**Target Persistence Engine:** Google Cloud Firestore (Datastore / Native Mode NoSQL)

---

## Executive Summary

This document presents a comprehensive, zero-code architectural migration plan to transition the LankaPay ID payment backend from a relational persistence model (MySQL 8 + Spring Data JPA + Hibernate + Flyway) to a highly available, globally distributed NoSQL document store (Google Cloud Firestore).

Transitioning a real-time financial settlement system to Firestore requires addressing key distributed database challenges:
1. **Financial Precision:** Replacing SQL `DECIMAL(15,2)` with `long minor units` (cents) + ISO-4217 `currency` code to eliminate IEEE 754 floating-point inaccuracies.
2. **Unique Constraints & Atomicity:** Emulating relational table unique constraints (phone numbers, NICs, idempotency keys) using deterministic document IDs or transactional reservation patterns.
3. **Optimistic Locking:** Replacing Hibernate `@Version` with Firestore transactional preconditions (`transaction.get()` $\rightarrow$ `transaction.update()`).
4. **Complex Queries & Aggregations:** Deconstructing relational `JOIN` and `OR` predicates into denormalized document fields, composite indexes, and `array-contains` queries.

---

## 1. Inventory of Relational & JPA Artifacts in `/backend`

### 1.1 JPA Entities (20 Entities)

| Module | Entity Class | Relational Table | Key Relationships & Foreign Keys |
| :--- | :--- | :--- | :--- |
| `identity` | `User` | `idn_users` | Root aggregate; 1:N RefreshToken, 1:N BankAccount, 1:1 Registry |
| `identity` | `Otp` | `idn_otps` | `@ManyToOne` $\rightarrow$ `User` (`user_id`) |
| `identity` | `OtpMetadata` | `idn_otp_metadata` | `@ManyToOne` $\rightarrow$ `User` (`user_id`); unique `(user_id, purpose)` |
| `identity` | `RefreshToken` | `idn_refresh_tokens` | `@ManyToOne` $\rightarrow$ `User` (`user_id`); unique `token_hash` |
| `bank` | `BankInstitution` | `bnk_institutions` | Standalone lookup; unique `bank_code` |
| `bank` | `BankAccount` | `bnk_accounts` | `@ManyToOne` $\rightarrow$ `User` (`user_id`); `@Version`; unique `account_number_hash` |
| `bank` | `AccountConsent` | `bnk_consents` | `@ManyToOne` $\rightarrow$ `User` (`user_id`), `@ManyToOne` $\rightarrow$ `BankAccount` (`bank_account_id`) |
| `registry` | `Registry` | `reg_registry` | `@OneToOne` $\rightarrow$ `User` (`user_id`); unique `user_id`, unique `proxy_value` |
| `registry` | `LookupAbuseCounter`| `reg_lookup_abuse_counters`| `@ManyToOne` $\rightarrow$ `User` (`user_id`); unique `(user_id, window_start)` |
| `transfer` | `Transfer` | `trf_transfers` | `@ManyToOne` $\rightarrow$ `sender` (`User`), `@ManyToOne` $\rightarrow$ `receiver` (`User`), `@ManyToOne` $\rightarrow$ `QrCode`; `@Version` |
| `transfer` | `TransactionEvent`| `trf_events` | `@ManyToOne` $\rightarrow$ `Transfer` (`transfer_id`) |
| `transfer` | `IdempotencyRecord`| `trf_idempotency_records`| Unique `idempotency_key` |
| `transfer` | `DailyLimit` | `trf_daily_limits` | `@ManyToOne` $\rightarrow$ `User` (`user_id`); unique `(user_id, limit_date)` |
| `qr` | `QrCode` | `qr_codes` | `@ManyToOne` $\rightarrow$ `merchant` (`User`); unique `reference`, unique `qr_id` |
| `qr` | `PaymentRequest` | `qr_payment_requests` | `@ManyToOne` $\rightarrow$ `requester` (`User`), `@ManyToOne` $\rightarrow$ `payer` (`User`), `@ManyToOne` $\rightarrow$ `QrCode`; unique `token` |
| `notification` | `Notification` | `not_notifications` | `@ManyToOne` $\rightarrow$ `User` (`user_id`) |
| `notification` | `NotificationPreference`| `not_preferences` | `@OneToOne` $\rightarrow$ `User` (`user_id`); unique `user_id` |
| `admin` | `RiskRules` | `adm_risk_rules` | Singleton policy document |
| `admin` | `FraudAlert` | `adm_fraud_alerts` | `@ManyToOne` $\rightarrow$ `User` (`user_id`), `@ManyToOne` $\rightarrow$ `Transfer` (`transfer_id`) |
| `audit` | `AuditLog` | `aud_audit_logs` | Append-only audit trail |

---

### 1.2 Spring Data JPA Repositories (20 Interfaces)

| Repository Interface | Entity Managed | Custom Queries & Features |
| :--- | :--- | :--- |
| `UserRepository` | `User` | `findByPhone`, `findByNicNumber`, `existsByPhone`, `existsByNicNumber`, `JpaSpecificationExecutor` |
| `OtpRepository` | `Otp` | JPQL multi-condition lookup (`findByUserIdAndPurposeAndVerifiedFalseAndExpiresAtAfter`), JPQL bulk delete (`deleteExpired`) |
| `OtpMetadataRepository` | `OtpMetadata` | `findByUserIdAndPurpose` |
| `RefreshTokenRepository`| `RefreshToken` | `findByTokenHash`, JPQL bulk update (`revokeAllForUser`) |
| `BankInstitutionRepository`| `BankInstitution` | `findByBankCode`, `findByActiveTrue` |
| `BankAccountRepository`| `BankAccount` | `findByUserId`, `findByUserIdAndIsDefaultTrue`, `findByAccountNumberHash`, JPQL bulk update (`clearDefaultForUser`) |
| `AccountConsentRepository`| `AccountConsent` | `findByUserIdAndBankAccountId` |
| `RegistryRepository` | `Registry` | `findByProxyValue`, `findByUserId`, JPQL join (`findUserByPhone`) |
| `LookupAbuseCounterRepository`| `LookupAbuseCounter`| `findByUserIdAndWindowStart` |
| `TransferRepository` | `Transfer` | `findByReference`, JPQL OR search (`findTransferForUser`), JPQL paged stream (`findAllForUser`), JPQL multi-field filter, `JpaSpecificationExecutor` |
| `TransactionEventRepository`| `TransactionEvent` | `findByTransferIdOrderByCreatedAtAsc` |
| `IdempotencyRecordRepository`| `IdempotencyRecord`| `findByIdempotencyKey` |
| `DailyLimitRepository`| `DailyLimit` | `findByUserIdAndLimitDate` |
| `QrCodeRepository` | `QrCode` | JPQL OR lookup (`findByRefOrQrId`), JPQL bulk update (`expirePastActiveQrs`) |
| `PaymentRequestRepository`| `PaymentRequest` | `findByToken`, JPQL bulk update (`expirePendingRequests`) |
| `NotificationRepository`| `Notification` | `findByUserIdOrderByCreatedAtDesc`, `findByUserIdAndReadFalse`, JPQL bulk update (`markAllAsReadForUser`), JPQL bulk delete |
| `NotificationPreferenceRepository`| `NotificationPreference`| `findByUserId` |
| `RiskRulesRepository`| `RiskRules` | `findTopByOrderByUpdatedAtDesc` |
| `FraudAlertRepository`| `FraudAlert` | `findByStatusOrderByCreatedAtDesc`, `findByUserId`, `JpaSpecificationExecutor` |
| `AuditLogRepository` | `AuditLog` | `findByActorIdOrderByTimestampDesc`, `findByTimestampBetween`, `JpaSpecificationExecutor` |

---

### 1.3 Flyway Migrations (10 Versioned Scripts)

All database DDL is managed by Flyway scripts in `backend/src/main/resources/db/migration/`:
- `V1__init_schema.sql`: Core tables (`idn_users`, `bnk_accounts`, `reg_registry`, `trf_transfers`, `aud_audit_logs`).
- `V2__seed_simulated_banks.sql`: Seed data for `SIMULATED_BANK_A`, `SIMULATED_BANK_B`, `SIMULATED_BANK_C`.
- `V3__add_login_lockout_columns.sql`: Account lockout columns on `idn_users`.
- `V4__add_otp_rate_limit_metadata.sql`: Rate limiting tables (`idn_otp_metadata`).
- `V5__add_bank_account_features.sql`: Masked columns and receiving toggles on `bnk_accounts`.
- `V6__add_lookup_abuse_counters.sql`: Abuse windows in `reg_lookup_abuse_counters`.
- `V7__add_transfer_state_machine_and_idempotency.sql`: State machine & `trf_idempotency_records`.
- `V8__add_section_10_qr_and_payment_requests.sql`: Dynamic QR and `qr_payment_requests`.
- `V9__add_notifications_preferences_and_transfer_bank_codes.sql`: In-app notification preferences.
- `V10__add_risk_rules_and_fraud_alerts.sql`: AML risk rule configuration and fraud alerting queues.

*In Firestore, schema migrations are eliminated in favor of application-level schema versioning and lazy field defaulting.*

---

### 1.4 Services Using `@Transactional`, `@Version`, Joins, and Foreign Keys

| Service / Class | JPA Features Utilized |
| :--- | :--- |
| `TransferService` | `@Transactional`, `@Transactional(readOnly = true)`, complex repository joins, pagination |
| `TransferProcessor` | `@Transactional`, optimistic locking check (`Transfer.@Version`), entity state mutations |
| `ReversalService` | `@Transactional`, updates `Transfer` and appends `TransactionEvent` atomically |
| `ReconciliationService` | `@Transactional`, reads pending transfers, calls bank adapter, mutates transfer status |
| `IdempotencyService` | `@Transactional(readOnly = true)`, `@Transactional(propagation = Propagation.REQUIRES_NEW)` |
| `DailyLimitService` | `@Transactional`, atomic increment on `DailyLimit` entity |
| `LookupService` | `@Transactional`, traverses `Registry` $\rightarrow$ `User` $\rightarrow$ `BankAccount` joins |
| `QrService` | `@Transactional`, generates QR entities and verifies user foreign keys |
| `PaymentRequestService` | `@Transactional`, checks expiration, executes transfers |
| `NotificationService` | `@Transactional`, bulk update JPQL queries |
| `PhoneVerificationService` | `@Transactional`, creates users and triggers OTP state |
| `OtpService` | `@Transactional`, deletes and validates OTPs |
| `AuthService` | `@Transactional`, updates login attempts, persists JWT refresh tokens |
| `BankAccountService` | `@Transactional`, optimistic locking check (`BankAccount.@Version`), clears default flags |
| `AuditService` | `@Transactional(propagation = Propagation.REQUIRES_NEW)`, writes decoupled audit logs |
| `RiskManagementService` | `@Transactional(readOnly = true)`, evaluates rules and logs `FraudAlert` |
| `SimulatedDataSeeder` | `@Transactional`, populates initial entity graph |

---

## 2. Proposed Firestore Document Modeling & Schema Design

### 2.1 Money Representation Standard
> **CRITICAL RULE:** Firestore does NOT have an arbitrary-precision decimal type. Storing monetary amounts as floating-point numbers (`double`) introduces catastrophic rounding errors (e.g. `0.1 + 0.2 = 0.30000000000000004`).  
> **All monetary amounts MUST be stored as `int64` (integer minor units / cents) accompanied by an ISO-4217 currency string.**  
> *Example:* `500.00 LKR` is stored as `{ "amountMinor": 50000, "currency": "LKR" }`.

---

### 2.2 Proposed Collection & Document Specifications

#### 1. Collection: `users`
- **Document ID:** Auto-generated UUID or alphanumeric ID (`userId`)
- **Fields:**
  ```json
  {
    "userId": "usr_94770000001",
    "phone": "+94770000001",
    "fullName": "Kasun Perera",
    "nicNumber": "199012345678",
    "role": "ROLE_USER",
    "status": "ACTIVE",
    "pinHash": "$2a$12$...",
    "failedLoginAttempts": 0,
    "lockoutUntil": null,
    "createdAt": "2026-10-11T10:00:00Z",
    "updatedAt": "2026-10-11T10:00:00Z"
  }
  ```
- **Denormalized Subcollections:**
  - `users/{userId}/accounts/{accountId}` (Linked bank accounts)
  - `users/{userId}/notifications/{notificationId}` (User notification stream)
  - `users/{userId}/refresh_tokens/{tokenId}` (Active login sessions)

#### 2. Unique Reservation Collections (Lookup & Constraint Tables)
To enforce unique constraints across distributed Firestore nodes:
- **`phones/{normalizedPhone}`:**
  - **Doc ID:** `+94770000001`
  - **Fields:** `{ "userId": "usr_94770000001", "claimedAt": "..." }`
- **`nics/{normalizedNic}`:**
  - **Doc ID:** `199012345678`
  - **Fields:** `{ "userId": "usr_94770000001", "claimedAt": "..." }`
- **`bank_account_hashes/{accountHash}`:**
  - **Doc ID:** `sha256_hash_of_account_number`
  - **Fields:** `{ "accountId": "acc_1001", "userId": "usr_94770000001" }`

#### 3. Collection: `registry` (Payment Identity Proxy Registry)
- **Document ID:** `normalizedPhone` (e.g., `+94770000001`)  
  *Enables single-read $O(1)$ lookups without needing secondary queries.*
- **Fields:**
  ```json
  {
    "phone": "+94770000001",
    "userId": "usr_94770000001",
    "displayName": "Kasun P.",
    "defaultBankAccountId": "acc_1001",
    "defaultBankCode": "SIMULATED_BANK_B",
    "defaultBankName": "Bank B (Hatton National)",
    "active": true,
    "frozenAt": null,
    "updatedAt": "2026-10-11T10:00:00Z"
  }
  ```
- **Denormalization:** Stores pre-masked `displayName` and default bank metadata directly on the registry document to eliminate joins during recipient lookups.

#### 4. Subcollection: `users/{userId}/accounts/{accountId}` (Bank Accounts)
- **Document ID:** Auto-generated UUID (`accountId`)
- **Fields:**
  ```json
  {
    "accountId": "acc_1001",
    "userId": "usr_94770000001",
    "bankCode": "SIMULATED_BANK_B",
    "bankName": "Bank B (Hatton National)",
    "accountNumberMasked": "****1001",
    "accountNumberHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "encryptedAccountRef": "AES_GCM_ENCRYPTED_STRING",
    "accountHolderName": "Kasun Perera",
    "isDefault": true,
    "receivingEnabled": true,
    "status": "VERIFIED",
    "version": 1,
    "createdAt": "2026-10-11T10:00:00Z",
    "updatedAt": "2026-10-11T10:00:00Z"
  }
  ```

#### 5. Collection: `transfers` (Financial Settlement Core)
- **Document ID:** `reference` (UUID string, e.g. `3b2a5987-a274-4b5b-9b4e-9827419fa76a`)
- **Fields:**
  ```json
  {
    "reference": "3b2a5987-a274-4b5b-9b4e-9827419fa76a",
    "idempotencyKey": "IDEM-KEY-001",
    "participantIds": ["usr_94770000001", "usr_94770000002"],
    "sender": {
      "userId": "usr_94770000001",
      "phone": "+94770000001",
      "fullName": "Kasun Perera",
      "bankCode": "SIMULATED_BANK_B",
      "accountMasked": "****1001"
    },
    "receiver": {
      "userId": "usr_94770000002",
      "phone": "+94770000002",
      "displayName": "Nimal P.",
      "bankCode": "SIMULATED_BANK_A",
      "accountMasked": "****1002"
    },
    "amountMinor": 50000,
    "feeMinor": 0,
    "currency": "LKR",
    "status": "COMPLETED",
    "providerReference": "SIM-B-1791700384",
    "failureCode": null,
    "failureReason": null,
    "remarks": "Lunch reimbursement",
    "qrId": null,
    "version": 2,
    "createdAt": "2026-10-11T12:00:00Z",
    "updatedAt": "2026-10-11T12:00:01Z",
    "completedAt": "2026-10-11T12:00:01Z"
  }
  ```
- **Denormalization:** Stores sender and receiver snapshots, eliminating runtime user table joins. `participantIds` array enables single-query bi-directional transaction history.

#### 6. Subcollection: `transfers/{reference}/events/{eventId}` (Transaction Audit Trail)
- **Document ID:** Sequential event counter or UUID (`eventId`)
- **Fields:**
  ```json
  {
    "transferReference": "3b2a5987-a274-4b5b-9b4e-9827419fa76a",
    "eventType": "BANK_INITIATED",
    "fromStatus": "INITIATED",
    "toStatus": "PENDING",
    "details": "Dispatched to SimulatedBankAdapterB",
    "eventHash": "SHA256:...",
    "previousEventHash": "SHA256:...",
    "createdAt": "2026-10-11T12:00:00.500Z"
  }
  ```

#### 7. Collection: `idempotency_records`
- **Document ID:** `idempotencyKey`  
  *Direct lookup enables atomic transaction acquisition.*
- **Fields:**
  ```json
  {
    "idempotencyKey": "IDEM-KEY-001",
    "transferReference": "3b2a5987-a274-4b5b-9b4e-9827419fa76a",
    "status": "COMPLETED",
    "responsePayload": "{\"id\":5001,\"reference\":\"3b2a5987...\"}",
    "createdAt": "2026-10-11T12:00:00Z",
    "expiresAt": "2026-10-12T12:00:00Z"
  }
  ```

#### 8. Collection: `daily_limits`
- **Document ID:** `{userId}_{YYYY-MM-DD}` (e.g., `usr_94770000001_2026-10-11`)
- **Fields:**
  ```json
  {
    "userId": "usr_94770000001",
    "limitDate": "2026-10-11",
    "cumulativeSpendMinor": 50000,
    "maxDailyLimitMinor": 10000000,
    "currency": "LKR",
    "transactionCount": 1,
    "updatedAt": "2026-10-11T12:00:01Z"
  }
  ```

#### 9. Collection: `qr_codes`
- **Document ID:** `reference` (e.g., `QREF-550e8400-e29b-41d4-a716-446655440000`)
- **Fields:**
  ```json
  {
    "reference": "QREF-550e8400-e29b-41d4-a716-446655440000",
    "qrId": "550e8400-e29b-41d4-a716-446655440000",
    "userId": "usr_94770000001",
    "bankCode": "SIMULATED_BANK_B",
    "payload": "lankapay://pay?id=+94770000001&bank=SIMULATED_BANK_B",
    "type": "DYNAMIC",
    "amountMinor": 120000,
    "currency": "LKR",
    "oneTimeUse": true,
    "status": "ACTIVE",
    "expiresAt": "2026-10-11T12:15:00Z",
    "createdAt": "2026-10-11T12:00:00Z"
  }
  ```

#### 10. Collection: `payment_requests`
- **Document ID:** `token` (e.g., `PR-9482-A7F2`)
- **Fields:**
  ```json
  {
    "token": "PR-9482-A7F2",
    "requesterId": "usr_94770000002",
    "amountMinor": 120000,
    "currency": "LKR",
    "qrPayload": "...",
    "status": "PENDING",
    "remarks": "Dinner split",
    "expiresAt": "2026-10-11T12:15:00Z",
    "createdAt": "2026-10-11T12:00:00Z"
  }
  ```

---

## 3. Unique Constraint Enforcement in Firestore

Firestore does not have native `UNIQUE` table indexes across multiple fields. We enforce uniqueness through two architectural patterns:

| Relational Constraint | Target Entity | Enforcement Pattern in Firestore | Implementation Detail |
| :--- | :--- | :--- | :--- |
| `phone` UNIQUE | `User` | **Document ID in Reservation Collection** | Write `phones/{phone}` in a Firestore transaction. If the document already exists, reject with `409 PHONE_ALREADY_REGISTERED`. |
| `nic_number` UNIQUE | `User` | **Document ID in Reservation Collection** | Write `nics/{nic}` in the same transaction. If the document exists, abort and rollback. |
| `token_hash` UNIQUE | `RefreshToken` | **Document ID Pattern** | Use `tokenHash` as the document ID: `refresh_tokens/{tokenHash}`. |
| `account_number_hash` UNIQUE | `BankAccount` | **Reservation Collection** | Write `bank_account_hashes/{hash}`. Guarantees an account cannot be linked by multiple profiles simultaneously. |
| `user_id` + `purpose` UNIQUE | `OtpMetadata` | **Composite Document ID** | Document ID is `{userId}_{purpose}` in `otp_metadata` collection. |
| `user_id` + `window_start` UNIQUE | `LookupAbuseCounter`| **Composite Document ID** | Document ID is `{userId}_{windowStartEpochMinute}`. |
| `idempotency_key` UNIQUE | `IdempotencyRecord` | **Document ID Pattern** | Set `idempotencyKey` as Document ID. `transaction.get(docRef)` determines if key is already reserved. |
| `user_id` + `limit_date` UNIQUE | `DailyLimit` | **Composite Document ID** | Document ID is `{userId}_{limitDate}` in `daily_limits`. |
| `reference` UNIQUE | `Transfer` | **Document ID Pattern** | Transfer UUID is the document ID: `transfers/{reference}`. |
| `token` UNIQUE | `PaymentRequest` | **Document ID Pattern** | Request token is document ID: `payment_requests/{token}`. |

---

## 4. Query Patterns & Firestore Composite Indexes

### 4.1 Required Firestore Composite Indexes

To support complex queries, the following composite indexes must be defined in `firestore.indexes.json`:

```json
{
  "indexes": [
    {
      "collectionGroup": "transfers",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "participantIds", "arrayConfig": "CONTAINS" },
        { "fieldPath": "createdAt", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "transfers",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "participantIds", "arrayConfig": "CONTAINS" },
        { "fieldPath": "status", "order": "ASCENDING" },
        { "fieldPath": "createdAt", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "transfers",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "status", "order": "ASCENDING" },
        { "fieldPath": "createdAt", "order": "ASCENDING" }
      ]
    },
    {
      "collectionGroup": "accounts",
      "queryScope": "COLLECTION_GROUP",
      "fields": [
        { "fieldPath": "userId", "order": "ASCENDING" },
        { "fieldPath": "isDefault", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "notifications",
      "queryScope": "COLLECTION_GROUP",
      "fields": [
        { "fieldPath": "userId", "order": "ASCENDING" },
        { "fieldPath": "read", "order": "ASCENDING" },
        { "fieldPath": "createdAt", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "qr_codes",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "status", "order": "ASCENDING" },
        { "fieldPath": "expiresAt", "order": "ASCENDING" }
      ]
    },
    {
      "collectionGroup": "payment_requests",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "status", "order": "ASCENDING" },
        { "fieldPath": "expiresAt", "order": "ASCENDING" }
      ]
    },
    {
      "collectionGroup": "fraud_alerts",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "status", "order": "ASCENDING" },
        { "fieldPath": "createdAt", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "audit_logs",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "actorId", "order": "ASCENDING" },
        { "fieldPath": "timestamp", "order": "DESCENDING" }
      ]
    }
  ]
}
```

---

### 4.2 Query Limitations & Architectural Workarounds

1. **Relational `OR` Predicate Across Different Fields:**
   - *JPA Query:* `WHERE (t.sender.id = :userId OR t.receiver.id = :userId) ORDER BY t.createdAt DESC`
   - *Firestore Limitation:* Firestore cannot perform an `OR` query across two distinct indexed fields while simultaneously sorting on a third field (`createdAt`).
   - *Workaround:* Denormalize both user IDs into an array field: `"participantIds": [senderId, receiverId]`. Query using:
     ```java
     firestore.collection("transfers")
       .whereArrayContains("participantIds", userId)
       .orderBy("createdAt", Query.Direction.DESCENDING)
     ```

2. **Bulk SQL Updates & Deletes:**
   - *JPA Query:* `UPDATE Notification n SET n.read = true WHERE n.user.id = :userId AND n.read = false`
   - *Firestore Limitation:* No in-place server-side bulk update statement (`UPDATE ... WHERE`).
   - *Workaround:* Read matching document IDs and commit an atomic `WriteBatch` (max 500 operations per batch).

3. **Scheduled Cleanup of Expired Entities:**
   - *JPA Query:* `DELETE FROM Otp o WHERE o.expiresAt < :cutoff`
   - *Firestore Solution:* Enable Firestore **TTL (Time-To-Live)** policy on the `expiresAt` timestamp field, allowing Firestore to delete expired OTP documents automatically with zero billable write operations.

---

## 5. Test Suites Dependent on MySQL / Testcontainers

The following test suites in `/backend` currently rely on a relational database engine and must be adapted during migration:

| Test Class | Relational Dependency | Migration Replacement |
| :--- | :--- | :--- |
| [`SmokeTest.java`](file:///q:/MY%20Projects/Lanka%20Pay/backend/src/test/java/com/lankapay/SmokeTest.java) | Direct `@Testcontainers` with `MySQLContainer("mysql:8.0")`. Tests Flyway and Spring datasource. | Replace with `FirestoreEmulatorContainer` (from Testcontainers) or native Spring Boot Firestore health check. |
| [`AuthControllerTest.java`](file:///q:/MY%20Projects/Lanka%20Pay/backend/src/test/java/com/lankapay/identity/web/AuthControllerTest.java) | Direct `@Testcontainers` with `MySQLContainer("mysql:8.0")` to boot the full security filter chain. | Replace datasource configuration with Google Cloud Firestore Local Emulator. |
| [`application-test.yml`](file:///q:/MY%20Projects/Lanka%20Pay/backend/src/test/resources/application-test.yml) | Defines `jdbc:tc:mysql:8.0:///lankapay_test` and `ContainerDatabaseDriver`. | Remove JDBC datasource configuration. Configure `spring.cloud.gcp.firestore.host-port: localhost:8080` for testing. |
| [`DemoScenarioE2ETest.java`](file:///q:/MY%20Projects/Lanka%20Pay/backend/src/test/java/com/lankapay/e2e/DemoScenarioE2ETest.java) | Uses Mockito repository mocks. **No Docker dependency.** | Update mocked repository signatures to return `CompletableFuture<DocumentSnapshot>` or Spring Data Firestore reactive/blocking models. |
| [`TransferConcurrencyAndLoadTest.java`](file:///q:/MY%20Projects/Lanka%20Pay/backend/src/test/java/com/lankapay/load/TransferConcurrencyAndLoadTest.java) | Uses Mockito mocks and concurrent in-memory latches. **No Docker dependency.** | Update repository mocks to simulate Firestore transactional write contention. |
| `All *ServiceTest.java` (15 unit test classes) | Mockito unit tests. | Clean migration; update mocked repository interfaces. |

---

## 6. Recommended Migration Order & Risk Analysis

### 6.1 Recommended Phased Migration Order

```
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 1: Persistence Abstraction & Firestore SDK Integration           │
│ - Introduce Spring Cloud GCP Firestore starter (spring-cloud-gcp-data) │
│ - Configure local Firestore Emulator in application-test.yml           │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│ Phase 2: Independent Peripheral Domains                                │
│ - Migrate Audit Logs (Append-only audit_logs)                          │
│ - Migrate Notification Preferences & Notifications (users/{id}/notifs) │
│ - Migrate OTP Metadata & Abuse Counters                                │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│ Phase 3: Identity & Bank Account Linking                               │
│ - Migrate Users with Reservation Collections (phones, nics)            │
│ - Migrate Bank Institutions & Bank Accounts (users/{id}/accounts)      │
│ - Migrate Registry (registry/{phone} proxy routing)                    │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│ Phase 4: Financial Core & Transaction Settlement                       │
│ - Migrate Idempotency Records (idempotency_records/{key})              │
│ - Migrate Daily Limits ({userId}_{date})                               │
│ - Migrate Transfers & Event Log (transfers/{ref}/events)               │
│ - Rewrite TransferProcessor to use Firestore RunTransaction            │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│ Phase 5: QR Code & Admin Operations                                    │
│ - Migrate QrCodes and PaymentRequests                                  │
│ - Migrate RiskRules and FraudAlerts                                    │
│ - Remove Flyway and MySQL dependencies from pom.xml                    │
└────────────────────────────────────────────────────────────────────────┘
```

---

### 6.2 Risk Analysis & Mitigations

| Risk Domain | Risk Description | Severity | Concrete Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| **Write Rate Contention on Hot Documents** | Firestore limits writes to a single document to **1 write per second**. If multiple transfers touch a single user's daily limit document simultaneously, transactions will fail or retry. | **High** | 1. Implement Distributed Counter Sharding (e.g. 5 shards per daily limit document).<br>2. Or maintain daily limits in memory with periodic batched flushes, backed by append-only transfer ledgers. |
| **Multi-Document ACID Rollback Failure** | Unlike MySQL where `@Transactional` rolls back arbitrary tables, Firestore transactions are limited to **500 documents** and cannot exceed 10 MB payload. | **High** | Keep financial transfer transactions small: a single transaction only touches the transfer record, the idempotency record, and the daily limit shard. |
| **Double-Spend via Race Conditions** | If idempotency reservation and daily spend check are executed in separate Firestore calls, duplicate payments could execute under load. | **Critical** | **Enforce Single Atomic Transaction:** Check idempotency key, verify daily limit, and create `Transfer` document within a single `firestore.runTransaction()` callback. |
| **Read Cost Amplification on Transaction History** | Querying large transaction streams without pagination creates high Firestore read costs. | **Medium** | Enforce strict query pagination using `limit(20)` and `.startAfter(lastVisibleDocument)` cursor pagination. |
| **Data Migration Downtime** | Exporting MySQL tables to Firestore JSON collections requires data transformation (e.g., converting decimals to cents, populating reservation documents). | **Medium** | Use a dual-write bridge or Spring Batch pipeline during the cutover window with verification checksums. |

---

## 7. Conclusion

Migrating LankaPay ID to Firestore provides horizontal scalability, low-latency regional replication, and simplifies cloud operations by eliminating relational connection pooling and schema migrations. By strictly applying the **Minor Unit (Cents) Standard**, **Reservation Document Pattern**, and **Denormalized Participant Arrays**, the system retains 100% of its financial safety guarantees without compromising NoSQL performance.
