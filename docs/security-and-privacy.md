# Security and Privacy Architecture & Verification

> **Lanka Pay Prototype – Security & Privacy Specification**  
> Document version: 1.0.0  
> Target: `/backend` service against [`docs/threat-model.md`](file:///q:/MY%20Projects/Lanka%20Pay/docs/threat-model.md) and Master Prompt Section 13.

---

## 1. Executive Summary & Security Philosophy

The Lanka Pay backend implements defense-in-depth security engineered around Zero-Trust principles for mobile-first financial operations:
- **Authentication**: Stateless HMAC-SHA256 JWT tokens with short time-to-live (30 minutes), server-side hashed refresh tokens, and dedicated step-up PIN verification for high-risk operations.
- **Authorization**: Mandatory object-level authorization (IDOR protection) on every transaction, bank account, payment request, and notification query. Users can never inspect or mutate records belonging to other tenants.
- **Cryptography**: AES-256-GCM authenticated encryption for sensitive bank account references, salted BCrypt hashing for credentials and PINs, and tamper-resistant SHA-256 payload hashing for idempotency replay detection.
- **Data Protection & Privacy**: Zero account number/PII disclosure on lock-screen push notifications, automated masking of phone numbers, national identity cards (NIC), and account identifiers across all application log streams.
- **Simulated Financial Boundary**: Strict structural isolation ensuring simulated mock adapters explicitly mark responses (`simulated: true`) with no real-world financial transaction execution.

---

## 2. Security Findings & Remediations Log

During comprehensive review of the entire `/backend` codebase against the threat model and Section 13 specifications, the following vulnerabilities and gaps were cataloged and resolved:

| ID | Severity | File | Issue Description | Remediation Applied |
|---|---|---|---|---|
| **SEC-01** | **High** | [`ReconciliationService.java`](file:///q:/MY%20Projects/Lanka%20Pay/backend/src/main/java/com/lankapay/transfer/service/ReconciliationService.java) & [`TransferController.java`](file:///q:/MY%20Projects/Lanka%20Pay/backend/src/main/java/com/lankapay/transfer/web/TransferController.java) | **Missing Object-Level Authorization (IDOR)**: `POST /api/v1/transfers/{id}/reconcile` allowed any authenticated user to trigger manual reconciliation on arbitrary transfer IDs without verifying if the caller was the sender or receiver. | Added overloaded `reconcileTransfer(Long transferId, Long authenticatedUserId)` in `ReconciliationService` that enforces sender/receiver ownership and throws `AccessDeniedException` (HTTP 403 Forbidden). Injected `@AuthenticationPrincipal Long userId` in `TransferController`. |
| **SEC-02** | **Medium** | [`AbstractSimulatedBankAdapter.java`](file:///q:/MY%20Projects/Lanka%20Pay/backend/src/main/java/com/lankapay/bank/adapter/AbstractSimulatedBankAdapter.java) | **Sensitive Data Leakage in Logs**: During account eligibility checks, the bank adapter logged raw account identifier (`request.identifier()`) directly into the console/log stream. | Replaced raw identifier logging with `last4(request.identifier())` masking (`identifier=****{}`), preserving auditability without exposing sensitive credentials or full account numbers. |
| **SEC-03** | **Medium** | [`GlobalExceptionHandler.java`](file:///q:/MY%20Projects/Lanka%20Pay/backend/src/main/java/com/lankapay/common/web/GlobalExceptionHandler.java) | **Information Leakage via Unhandled Exceptions**: Malformed JSON bodies (`HttpMessageNotReadableException`), route parameter type mismatches (`MethodArgumentTypeMismatchException`), and unsupported HTTP methods defaulted to unhandled 500 Internal Server Error handlers, potentially exposing framework stack traces. | Added explicit `@ExceptionHandler` mappings returning clean, standardized `ApiResponse.error(...)` payloads: HTTP 400 for malformed JSON/type mismatch, HTTP 405 for unsupported method, and HTTP 415 for unsupported media types. |
| **SEC-04** | **Medium** | [`SecurityConfig.java`](file:///q:/MY%20Projects/Lanka%20Pay/backend/src/main/java/com/lankapay/auth/config/SecurityConfig.java) | **Missing Security Headers & CORS Lockdown**: Lack of defensive HTTP response headers (CSP, HSTS, X-Frame-Options, Referrer-Policy, Permissions-Policy) and permissive default CORS configuration. | Added `corsConfigurationSource()` bean with controlled headers (`Idempotency-Key`, `X-Step-Up-Pin`, etc.) and configured HTTP response headers: `FrameOptions.deny()`, strict Content Security Policy (`default-src 'self'; frame-ancestors 'none'`), HSTS (1 year max-age), Referrer Policy, and Permissions Policy. |
| **SEC-05** | **High** | [`AccountReferenceEncryptor.java`](file:///q:/MY%20Projects/Lanka%20Pay/backend/src/main/java/com/lankapay/bank/crypto/AccountReferenceEncryptor.java) & [`JwtService.java`](file:///q:/MY%20Projects/Lanka%20Pay/backend/src/main/java/com/lankapay/auth/security/JwtService.java) | **Hardcoded / Default Secrets in Config**: Fallback placeholder secrets in `.env.example` or dev profiles could accidentally be carried over to production deployments without verification. | Implemented fail-fast production checks checking `@Value("${spring.profiles.active:default}")`. If `prod` or `production` profile is active and known dummy/short test keys are detected, startup halts immediately with `IllegalStateException`. |
| **SEC-06** | **Low** | [`pom.xml`](file:///q:/MY%20Projects/Lanka%20Pay/backend/pom.xml) | **Lack of Automated Vulnerability Scanning**: Maven build had no static software composition analysis (SCA) configured to detect vulnerable third-party dependencies. | Integrated `dependency-check-maven` (v10.0.4) with CVSS score threshold failure (`failBuildOnCVSS = 8.0`) and HTML/JSON report generation. |

---

## 3. Threat Model Verification & Mitigations Summary

Referencing the STRIDE matrix in [`docs/threat-model.md`](file:///q:/MY%20Projects/Lanka%20Pay/docs/threat-model.md):

### 3.1 T-01: Phone Number & Identity Enumeration
- **Mitigation**: `PaymentIdentityService` and lookup endpoints implement bucket-based rate limiting per IP/user. Unregistered phones and un-discoverable identities (`discoverable = false`) both return HTTP 404 with zero PII disclosed.

### 3.2 T-02: OTP Brute Force & Credential Attacks
- **Mitigation**: 6-digit cryptographic OTPs expire in 5 minutes. Account is locked for 15 minutes after 5 consecutive failures. Passwords and PINs are BCrypt-hashed with per-tenant salt.

### 3.3 T-03: Replay Attacks & Transaction Tampering
- **Mitigation**: `Idempotency-Key` header is mandatory on `POST /transfers`.
  - Same key + identical payload: returns cached HTTP 200/201 response with `idempotentReplay: true`.
  - Same key + altered payload: immediately rejected with HTTP 409 Conflict.
  - Keys are tracked in `idempotency_records` table with unique constraint.

### 3.4 T-04: Insecure Direct Object References (IDOR)
- **Mitigation**: All controllers query services with `@AuthenticationPrincipal Long userId`.
  - `GET /transfers/{id}`, `GET /transfers/{id}/receipt`, `POST /transfers/{id}/reconcile`: Verified against `senderUserId` or `receiverUserId`.
  - `GET /bank-accounts/{id}`: Verified against `userId`.
  - `GET /notifications`: Filters strictly by caller's `userId`.
  - `POST /payment-requests`: Verified against creator/recipient.
  - Non-authorized calls return HTTP 403 Forbidden without leaking resource details.

### 3.5 T-05: Double / Duplicate Payments & Concurrency Races
- **Mitigation**:
  - Unique database constraints on `(sender_id, idempotency_key)`.
  - Transaction state machine ([`TransactionStateMachine.java`](file:///q:/MY%20Projects/Lanka%20Pay/backend/src/main/java/com/lankapay/transfer/domain/TransactionStateMachine.java)) strictly enforces unidirectional transitions: `PENDING -> PROCESSING -> COMPLETED -> REVERSAL_PENDING -> REVERSED`. Terminal states (`COMPLETED`, `FAILED`, `REVERSED`) reject transitions back to `PENDING` or `PROCESSING`.
  - Optimistic locking (`@Version`) on account limits and transfer entities prevents simultaneous double debits.

### 3.6 T-06: QR Code Tampering
- **Mitigation**: Scanning a QR code merely resolves a server-side opaque reference (`GET /qr/references/{ref}`). The client cannot inject arbitrary recipient details or bypass transfer authorization. Dynamic QR payments enforce server-side expiration and single-use invalidation.

### 3.7 T-07: Admin Privilege Abuse & Audit Logging
- **Mitigation**: Endpoints under `/api/v1/admin/**` require `ROLE_ADMIN`. Every administrative action (status updates, manual reconciliations, fraud reviews) creates an immutable record in `aud_audit_log` capturing actor, action, timestamp, target entity, and IP address. Admins cannot view passwords, OTPs, or user PINs.

### 3.8 T-10: PII Leakage in Logs & Notifications
- **Mitigation**:
  - `MaskingUtils`: Masks phone numbers (`+9477***1234`), NIC numbers, and account identifiers (`****1234`).
  - Lock-screen push/SMS notifications contain generic alerts (*"You have received a transfer"*) without revealing amounts, balances, or account numbers.

---

## 4. Software Composition Analysis (SCA)

The Maven build is integrated with OWASP Dependency Check:
- **Plugin**: `org.owasp:dependency-check-maven:10.0.4`
- **Execution Profile**: Configured to run during `verify` phase or on demand via `mvn dependency-check:check -Ddependency-check.skip=false`.
- **Policy**: Fails build on CVSS score $\ge 8.0$.
- **Suppression / Scope**: Excludes non-runtime test plugins; generates audit reports in `target/dependency-check-report.html`.

---

## 5. Security Regression Test Suite

An automated regression suite is maintained in [`SecurityRegressionTest.java`](file:///q:/MY%20Projects/Lanka%20Pay/backend/src/test/java/com/lankapay/security/SecurityRegressionTest.java) containing 17 targeted automated tests:
1. **IDOR Suite**:
   - `cannotAccessOtherUserTransfer`: Verifies HTTP 403 on viewing another user's transfer.
   - `cannotAccessOtherUserReceipt`: Verifies HTTP 403 on viewing another user's receipt.
   - `cannotReconcileOtherUserTransfer`: Verifies HTTP 403 on unauthorized reconciliation attempts.
   - `senderOrReceiverCanAccessTransfer`: Verifies valid participants retain authorized access.
   - `cannotAccessOtherUserBankAccount`: Verifies HTTP 403 on viewing foreign bank accounts.
   - `cannotAccessOtherUserPaymentRequest`: Verifies HTTP 403 on foreign payment request lookup.
   - `cannotAccessOtherUserNotifications`: Verifies HTTP 403 on foreign notifications.
   - `unauthenticatedUserCannotAccessProtectedEndpoints`: Verifies HTTP 401 across all endpoints.
2. **Enumeration Suite**:
   - `unknownIdentityReturns404WithNoPii`: Verifies no PII leaked on non-existent users.
   - `nonDiscoverableIdentityReturns404WithNoPii`: Verifies no PII leaked when discovery is disabled.
   - `rateLimiterBlocksRapidEnumeration`: Verifies HTTP 429 after burst threshold exceeded.
3. **Replay & Idempotency Suite**:
   - `replayedTransferWithSamePayloadReturnsCachedResult`: Verifies identical payload returns 200 without duplicate execution.
   - `replayedTransferWithModifiedPayloadReturnsConflict`: Verifies modified payload triggers HTTP 409 Conflict.
4. **Duplicate Payment & State Machine Suite**:
   - `cannotTransitionFromCompletedToFailed`: Rejects illegal status rewind.
   - `cannotTransitionFromCompletedToPending`: Rejects illegal transition back to pending.
   - `cannotTransitionFromFailedToCompleted`: Rejects reviving failed transaction without reconciliation.
   - `reversalAllowedOnlyFromCompleted`: Ensures refunds can only originate from settled transactions.

All 17 security regression tests and 291 existing unit/integration tests pass reliably (`Tests run: 308, Failures: 0, Errors: 0, Skipped: 10`).

---

## 6. Known Prototype Limitations & Production Recommendations

This system was engineered as an academic/industrial demonstration prototype. Before deployment in a regulated production environment under Central Bank of Sri Lanka (CBSL) oversight, the following controls must be addressed:

1. **Hardware Security Module (HSM)**: Account reference encryption (`AccountReferenceEncryptor`) currently uses an environment-configured AES-256 key. Production systems should integrate with a cloud KMS (AWS KMS / GCP Cloud KMS / HashiCorp Vault) or hardware security module with automated key rotation.
2. **Device-Level Attestation**: The mobile client relies on standard TLS. Production releases must enforce SSL Pinning, SafetyNet/Play Integrity API, and jailbreak/root detection on Android/iOS.
3. **Simulated Bank Adapter Boundary**: All bank communications are routed through `AbstractSimulatedBankAdapter`. While this safely mirrors real clearing behavior, real banking integration requires ISO 20022 / CEFTS message signing, mutual TLS (mTLS), and dedicated host-to-host VPN connections.
4. **Anti-Enumeration Response Uniformity**: While `GET /api/v1/payment-identities/lookup` returns HTTP 404 for both non-existent users and discoverable=false users, the JSON payload returns `NOT_FOUND` vs `DISCOVERY_DISABLED`. In high-threat environments, response payloads and timing should be strictly unified to prevent differential side-channel analysis.
5. **WAF & DDoS Mitigation**: Network-level volumetric DDoS defense, IP reputation filtering, and Web Application Firewall (WAF) rules must sit in front of the application ingress.
