# Lanka Pay – Virtual Card Module Plan

> University prototype. All card numbers are TEST DATA. No real card networks, no real PCI
> scope. The word "SIMULATED" must appear on every card-related API response, UI element,
> and log message.
>
> **Do NOT use or display any card-network logos anywhere. Use the text label `TEST CARD`
> instead.**

---

## 1. How the Existing Backend is Structured

### 1.1 Persistence — Firestore, not SQL

The backend uses **Google Cloud Firestore** (via Firebase Admin SDK 9.3.0) as its sole
persistence layer. There are **no SQL tables, no Flyway migrations, and no JDBC/JPA
dependencies**. The `docs/architecture.md` mentions MySQL/Flyway as an aspirational
target, but the running codebase replaced that with Firestore. Collection names follow the
`bnk_`, `trf_`, `aud_` prefix convention from the original design.

The virtual card module must follow the same pattern:
- A **Store interface** (port) in `virtualcard/domain/VirtualCardStore.java`.
- A **Firestore implementation** in `virtualcard/store/firestore/FirestoreVirtualCardStore.java`.
- A **Firestore collection** named `vc_virtual_cards` (documents keyed by card ID).
- A separate `vc_card_consents` collection for append-only consent records.

There is no migration script to write. Schema is implicit in the document structure.

### 1.2 Application Configuration

All sensitive values come from environment variables. Existing config keys in
`application.yml` that the card module depends on or must extend:

| Key | Purpose |
|---|---|
| `app.encryption.bank-account-key` | AES-256-GCM key for bank account references |
| `app.otp.mode` / `app.otp.expiry-seconds` | OTP delivery and TTL |
| `app.jwt.secret` / `app.jwt.expiry-minutes` | JWT signing |
| `app.bank-adapter.mode` | `SIMULATED` (default) |
| `app.lookup.rate-limit-per-user-per-minute` | Bucket4j per-user rate limiting |

**New keys to add for virtual cards:**

```yaml
app:
  encryption:
    virtual-card-key: ${VIRTUAL_CARD_ENCRYPTION_KEY:}  # AES-256-GCM, 32-byte Base64-URL

  virtual-card:
    card-adapter-mode: ${CARD_ADAPTER_MODE:SIMULATED}
    tokenize-rate-limit-per-user-per-minute: ${CARD_TOKENIZE_RATE_LIMIT:3}
    tokenize-rate-limit-per-ip-per-minute: ${CARD_TOKENIZE_RATE_LIMIT_IP:5}
    pin-reveal-rate-limit-per-user-per-day: ${CARD_REVEAL_RATE_LIMIT:3}
    max-cards-per-user: ${CARD_MAX_PER_USER:3}
```

### 1.3 Existing Module Structure the Card Module Must Follow

```
com.lankapay/
  bank/          ← reference pattern for domain, store, service, web, crypto
  transfer/      ← reference pattern for idempotency, state machine, processor
  identity/      ← step-up PIN auth via X-Step-Up-Pin header
  audit/         ← AuditService.log() — REQUIRES_NEW, append-only
  notification/  ← NotificationService — fire-and-forget, never propagates failures
  registry/      ← LookupRateLimiter — Bucket4j per-user + per-IP pattern
  admin/         ← RiskManagementService — risk rules, fraud alert raising
  common/        ← exceptions, ErrorResponse, GlobalExceptionHandler, encryptors
```

The new module goes in `com.lankapay.virtualcard` with sub-packages:
`domain/`, `service/`, `store/`, `web/`, `crypto/`, `provider/`

### 1.4 Key Patterns Every Module Follows

| Pattern | Where used | Card module must do the same |
|---|---|---|
| **AES-256-GCM at rest** | `AccountReferenceEncryptor` | New `VirtualCardEncryptor` for token (never PAN) |
| **SHA-256 hash for dedup** | `BankAccountService.linkAccount()` | Hash PAN before passing to vault; store hash for duplicate detection |
| **Soft-delete only** | `BankAccountStatus.REMOVED` | `VirtualCardStatus.TERMINATED` — never physical delete |
| **Consent records** | `AccountConsentRecord`, `vc_card_consents` | Write before issue, freeze, terminate |
| **Optimistic locking** | `version` field on Transfer, BankAccount | `version` field on VirtualCard |
| **Object-level auth** | `requireOwnedAccount(id, userId)` returns 403 not 404 | `requireOwnedCard(id, userId)` same pattern |
| **Step-up PIN** | `X-Step-Up-Pin` header in TransferController | Required for tokenize and PAN reveal |
| **Idempotency** | `Idempotency-Key` header + SHA-256 payload hash | Required for card-funded payments |
| **Audit every change** | `auditService.log()` on every state transition | `VIRTUAL_CARD_ISSUED`, `VIRTUAL_CARD_FROZEN`, etc. |
| **Notifications fire-and-forget** | `notificationService.notify*()` in try/catch | Same |
| **Bucket4j rate limiting** | `LookupRateLimiter` per-user + per-IP | New `CardRateLimiter` for tokenize + reveal |
| **Risk evaluation** | `riskManagementService.evaluateTransferRisk()` | `evaluateVirtualCardRisk()` extension |
| **simulated: true in responses** | `AdapterResponse.simulated` | Every card API response carries `"simulated": true` |
| **State machine** | `TransactionStateMachine.validateTransition()` | `VirtualCardStateMachine` (see §3d) |

---

## 2. Where a Transfer Picks Its Funding Source and What Must Change

### 2.1 Current Flow (Bank Account Only)

In `TransferService.initiateTransfer()`, the funding source is resolved by:

```java
// Step 7 — resolve bank accounts
BankAccount senderAccount = resolveSenderAccount(sender.getId());
BankAccount payeeAccount  = resolvePayeeAccount(payee.getId());
```

`resolveSenderAccount()` calls `linkedAccountStore.findActiveDefaultAccount(userId)`,
falling back to the first active account. It throws `NO_ACTIVE_BANK_ACCOUNT` if none
exists.

`TransferProcessor.process()` then calls:
```java
BankAdapter adapter = bankAdapterRegistry.get(senderAccount.getBankCode());
adapter.checkTransferEligibility(eligRequest);
adapter.initiateTransfer(txRequest);
```

The adapter is selected by `bankCode` (e.g. `"SIMULATED_BANK_A"`). The debit and credit
are both handled by the same `BankAdapter` instance today.

### 2.2 What Must Change to Support "Paid From a Card"

The payment-rail is currently hardwired to `BankAdapter`. To support virtual cards as a
funding source, three things must change:

**a. `InitiateTransferRequest` gets an optional `fundingSource` discriminator:**

```java
public record InitiateTransferRequest(
    // existing fields …
    FundingSource fundingSource,         // NEW — null means "use default bank account"
    Long virtualCardId                   // NEW — required when fundingSource == VIRTUAL_CARD
)

public enum FundingSource { BANK_ACCOUNT, VIRTUAL_CARD }
```

**b. `TransferService.resolveSenderAccount()` becomes `resolveFundingSource()`:**

```java
FundingDetails resolveFundingSource(Long userId, FundingSource source, Long virtualCardId) {
    if (source == FundingSource.VIRTUAL_CARD) {
        VirtualCard card = virtualCardService.requireOwnedActiveCard(virtualCardId, userId);
        return FundingDetails.fromCard(card);
    }
    // existing bank account resolution
    return FundingDetails.fromBankAccount(resolveSenderAccount(userId));
}
```

**c. `TransferProcessor.process()` dispatches to the correct provider:**

```java
if (funding.isCard()) {
    // debit the virtual card via CardProvider
    CardAuthResponse authResp = cardProvider.authorize(cardAuthRequest);
    if (!authResp.approved()) { ... throw ... }
    // credit the receiver's bank account as today
    adapter.credit(creditRequest);
} else {
    // existing bank adapter flow unchanged
    adapter.checkTransferEligibility(...);
    adapter.initiateTransfer(...);
}
```

**d. `TransactionRecord` / `TransactionStore` needs two extra nullable fields:**

| Field | Type | Notes |
|---|---|---|
| `fundingSource` | `FundingSource` | `BANK_ACCOUNT` or `VIRTUAL_CARD` |
| `fundingCardId` | `Long` | nullable; set when `fundingSource == VIRTUAL_CARD` |

**e. No changes to the state machine, idempotency, risk, daily-limit, or audit layers —
they are funding-source-agnostic.**

**f. Daily limit tracking:** `DailyLimitService.checkLimit()` and `recordSpend()` already
operate on amounts only. Card-funded transfers count against the same daily cap, but also
against the card's own per-card spend limit (enforced by `CardProvider` before the
transfer is created).

---

## 3. Module Design

### 3a. Simulated Card Vault (`com.lankapay.cardvault`)

The Card Vault is the **only code allowed to see a full card number (PAN), expiry, or
CVV**. Everything else in the system sees only an opaque `cardToken`.

#### Purpose

- Accept a raw PAN + expiry + CVV from the mobile app over TLS.
- Validate it against a fixed seed table of TEST cards.
- Reject every number not in that table with `CARD_NOT_ACCEPTED`.
- Return an opaque `cardToken` (UUID) tied to the validated card.
- Expose a `CardVault.authorize(token, amount, currency, operationRef)` method for
  transfers.

#### Architecture constraint

The vault runs **in-process** in this prototype (a separate package within the same Spring
Boot JAR). In production it would be a separate PCI-scoped service. The module boundary
is enforced by making `CardVault` a Spring `@Component` with package-private internals:
no other package may import `CardVaultInternalRecord` or any raw-PAN type.

#### Seeded TEST Cards

| Token (auto-generated) | Last 4 | Brand label | Expiry | Balance | Scenario |
|---|---|---|---|---|---|
| auto | 4242 | TEST CARD | 12/27 | 50,000 LKR | Always success |
| auto | 0002 | TEST CARD | 12/27 | 50,000 LKR | Insufficient funds |
| auto | 0069 | TEST CARD | 12/27 | 50,000 LKR | Timeout (→ REQUIRES_RECONCILIATION) |
| auto | 0119 | TEST CARD | 12/27 | 0 LKR | Hard decline |
| auto | 9995 | TEST CARD | 12/26 | — | Expired card |

Scenarios are triggered by `last4`, identical to the `TransferScenario` magic-amount
pattern in `SimulatedBankAdapter`.

#### Interfaces

```java
// The only public surface of the vault (in com.lankapay.cardvault)
public interface CardVault {

    /** Tokenize a raw PAN+CVV. Called once during card add. Returns opaque token. */
    TokenizeResponse tokenize(TokenizeRequest request);

    /** Authorize a debit against a previously issued token. */
    CardAuthResponse authorize(CardAuthRequest request);

    /** Void/reverse a previous authorization (for transfer reversals). */
    CardAuthResponse reverse(CardReverseRequest request);

    /** Query status of a previous authorization. */
    CardAuthResponse queryAuth(CardAuthQueryRequest request);
}
```

```java
public record TokenizeRequest(
    String pan,        // 16-digit PAN — never leave this class alive
    String expiryMM,   // 2-digit month
    String expiryYY,   // 2-digit year
    String cvv,        // 3 or 4 digits — discarded after tokenization
    String operationReference
)

public record TokenizeResponse(
    String cardToken,  // opaque UUID — the only identifier stored outside the vault
    String last4,
    String expiryMM,
    String expiryYY,
    String brandLabel, // always "TEST CARD"
    boolean simulated
)
```

```java
public record CardAuthResponse(
    boolean approved,
    String authCode,          // vault-side authorization code
    String cardToken,
    CardAuthStatus status,    // APPROVED | DECLINED | TIMEOUT | REVERSED
    String declineReason,     // null on approval
    boolean simulated
)
```

#### Security rules for the vault

- `pan` and `cvv` fields in `TokenizeRequest` must be **zero-filled immediately** after
  the tokenize operation completes (set to `"0000000000000000"` before GC).
- `TokenizeRequest` must never be serialized to JSON, logged, or included in a stack trace.
- All vault internals (`VaultCard`, `VaultStore`) are package-private; zero exported types
  carry PAN.
- The vault's in-memory seed table is the **only place a PAN exists** in the system.
  No database, no Firestore, no log ever receives it.

---

### 3b. Card Module — What It Stores

Package: `com.lankapay.virtualcard`

The card module stores only the **opaque token** returned by the vault plus safe metadata.

#### VirtualCard domain record

```
Firestore collection: vc_virtual_cards
Document ID:          card ID (auto-generated Long → String)
```

| Field | Type | Notes |
|---|---|---|
| `id` | Long | Document key |
| `userId` | Long | Owner — never expose in any response |
| `cardToken` | String | Opaque UUID from vault — AES-GCM encrypted at rest with `virtual-card-key` |
| `tokenHash` | String | SHA-256(cardToken) — for dedup + lookup without decrypting |
| `last4` | String | Safe to display |
| `expiryMonth` | String | Safe to display |
| `expiryYear` | String | Safe to display |
| `brandLabel` | String | Always `"TEST CARD"` |
| `nickname` | String | Optional user label |
| `status` | VirtualCardStatus | State machine value |
| `spendLimitPerTransaction` | BigDecimal | Default 10,000 LKR |
| `spendLimitDaily` | BigDecimal | Default 50,000 LKR |
| `linkedBankAccountId` | Long | Nullable — for top-up/cashback routing |
| `simulated` | boolean | Always `true` |
| `version` | Long | Optimistic locking |
| `issuedAt` | Instant | Immutable after issue |
| `updatedAt` | Instant | Updated on every state change |

**NEVER stored:** Full PAN, CVV, PIN, card network name, card network logo asset path.

#### VirtualCardConsent record

```
Firestore collection: vc_card_consents
```

| Field | Type |
|---|---|
| `id` | Long |
| `userId` | Long |
| `cardId` | Long |
| `consentType` | VirtualCardConsentType |
| `consentGiven` | boolean |
| `consentIp` | String |
| `consentedAt` | Instant |

`VirtualCardConsentType` enum: `ISSUE_CARD`, `FREEZE_CARD`, `TERMINATE_CARD`,
`LINK_BANK_ACCOUNT`, `SET_SPENDING_LIMIT`

---

### 3c. CardProvider Interface

Package: `com.lankapay.virtualcard.provider`

```java
/**
 * In-process card provider contract.
 * The only module allowed to call CardVault is an implementation of this interface.
 * All other code calls CardProvider exclusively.
 */
public interface CardProvider {

    String getProviderCode();   // "SIMULATED" or "FUTURE_GATEWAY"

    /** Issue a new virtual card. Returns the card token + metadata. Never returns PAN. */
    CardProviderResponse issue(CardIssueRequest request);

    /** Freeze / unfreeze a card. */
    CardProviderResponse updateStatus(CardStatusRequest request);

    /** Authorize a debit (called by TransferProcessor for card-funded transfers). */
    CardAuthResponse authorize(CardAuthRequest request);

    /** Reverse a prior authorization. */
    CardAuthResponse reverse(CardReverseRequest request);

    /** Query authorization status (called on TIMEOUT). */
    CardAuthResponse queryAuth(CardAuthQueryRequest request);

    /** Terminate (permanently cancel) a card. */
    CardProviderResponse terminate(CardTerminateRequest request);
}
```

#### SimulatedCardProvider

```java
@Component
@ConditionalOnProperty(name = "app.virtual-card.card-adapter-mode",
                       havingValue = "SIMULATED", matchIfMissing = true)
public class SimulatedCardProvider implements CardProvider {
    // Delegates to CardVault for tokenize and authorize.
    // Logs "[SIMULATED CARD]" prefix on every operation.
    // All responses carry simulated: true.
    // Scenario outcomes driven by last4 (see §3a seed table).
}
```

#### FutureGatewayCardProvider (stub)

```java
// NOT a Spring bean — not registered until CARD_ADAPTER_MODE=REAL_GATEWAY
public class FutureGatewayCardProvider implements CardProvider {
    // Every method throws:
    //   throw new UnsupportedOperationException(
    //     "FutureGatewayCardProvider is not implemented. "
    //     + "Set app.virtual-card.card-adapter-mode=SIMULATED for the simulator. "
    //     + "Implement this class with a real PCI gateway before enabling REAL_GATEWAY mode.");
}
```

A `CardProviderRegistry` bean (mirroring `BankAdapterRegistry`) routes by `providerCode`.

---

### 3d. Data Model, Endpoints, State Machine, and Scenarios

#### State Machine — VirtualCardStatus

```
PENDING_VERIFICATION ──(OTP verified)──► ACTIVE
ACTIVE               ──(freeze)────────► FROZEN
ACTIVE               ──(expire)────────► EXPIRED      (scheduled job)
ACTIVE               ──(terminate)─────► TERMINATED
FROZEN               ──(unfreeze)──────► ACTIVE
FROZEN               ──(terminate)─────► TERMINATED
EXPIRED              ──(no transition)─ terminal
TERMINATED           ──(no transition)─ terminal
```

Rules:
- `TERMINATED` and `EXPIRED` are terminal — no further transitions.
- Only ACTIVE cards can be used as a funding source.
- Freeze is reversible; terminate is not.
- Admin can freeze/unfreeze/terminate any card; user can freeze/unfreeze/terminate their own.

#### Firestore Collections

```
vc_virtual_cards/
  {cardId}/
    ...fields (see §3b)

vc_card_consents/
  {consentId}/
    ...fields (see §3b)

vc_card_auth_log/
  {authId}/
    cardId, userId, transferId, amount, currency,
    authCode, status, declineReason, simulated, occurredAt
```

#### API Endpoints

All under `/api/v1/virtual-cards`. All require `Authorization: Bearer <jwt>`.
Operations that move money or expose sensitive data also require `X-Step-Up-Pin`.

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/virtual-cards/tokenize` | JWT + X-Step-Up-Pin | Add a card (PAN+CVV sent here, token returned) |
| `GET` | `/virtual-cards` | JWT | List user's virtual cards (no PAN, no CVV) |
| `GET` | `/virtual-cards/{id}` | JWT | Get single card details (no PAN, no CVV) |
| `PATCH` | `/virtual-cards/{id}` | JWT | Update nickname, linkedBankAccountId |
| `PATCH` | `/virtual-cards/{id}/limits` | JWT | Update spend limits |
| `POST` | `/virtual-cards/{id}/freeze` | JWT + consent=true | Freeze card |
| `POST` | `/virtual-cards/{id}/unfreeze` | JWT | Unfreeze card |
| `DELETE` | `/virtual-cards/{id}` | JWT + X-Step-Up-Pin + consent=true | Terminate card |
| `GET` | `/virtual-cards/{id}/auth-log` | JWT | Card authorization history |

The `tokenize` endpoint **does not exist in any HTTP log** after processing — the
request body must not be logged by `LogInterceptor`, `AccessLog`, or any middleware.
See §4 for how to enforce this.

#### Request / Response Shape (key examples)

```json
// POST /virtual-cards/tokenize — Request
{
  "pan": "4111111111114242",
  "expiryMM": "12",
  "expiryYY": "27",
  "cvv": "123",
  "nickname": "My Test Card",
  "linkedBankAccountId": 7,
  "consentGiven": true
}

// POST /virtual-cards/tokenize — Response 201
{
  "cardId": 1,
  "last4": "4242",
  "expiryMM": "12",
  "expiryYY": "27",
  "brandLabel": "TEST CARD",
  "nickname": "My Test Card",
  "status": "ACTIVE",
  "simulated": true
}
```

```json
// GET /virtual-cards — Response 200
[
  {
    "cardId": 1,
    "last4": "4242",
    "expiryMM": "12",
    "expiryYY": "27",
    "brandLabel": "TEST CARD",
    "nickname": "My Test Card",
    "status": "ACTIVE",
    "spendLimitPerTransaction": "10000.00",
    "spendLimitDaily": "50000.00",
    "linkedBankAccountId": 7,
    "simulated": true,
    "issuedAt": "2026-10-10T10:00:00Z"
  }
]
```

#### Audit Events

| Event type | Trigger |
|---|---|
| `VIRTUAL_CARD_ISSUED` | Card moves to `ACTIVE` |
| `VIRTUAL_CARD_ISSUE_FAILED` | Vault rejects PAN |
| `VIRTUAL_CARD_FROZEN` | Freeze action |
| `VIRTUAL_CARD_UNFROZEN` | Unfreeze action |
| `VIRTUAL_CARD_TERMINATED` | Terminate action |
| `VIRTUAL_CARD_EXPIRED` | Scheduled expiry job |
| `VIRTUAL_CARD_LIMIT_UPDATED` | Spend limit change |
| `VIRTUAL_CARD_AUTH_APPROVED` | Authorization success |
| `VIRTUAL_CARD_AUTH_DECLINED` | Authorization declined |
| `VIRTUAL_CARD_AUTH_TIMEOUT` | Authorization timeout |
| `VIRTUAL_CARD_AUTH_REVERSED` | Authorization reversed |

`detailJson` for auth events: `{"last4":"4242","amount":"1500.00","currency":"LKR"}` —
never includes token, PAN, or CVV.

#### Scenarios (mirroring bank adapter scenario pattern)

| Trigger (last4) | Scenario | Vault returns | Transfer outcome |
|---|---|---|---|
| 4242 | SUCCESS | `APPROVED` | `COMPLETED` |
| 0002 | INSUFFICIENT_FUNDS | `DECLINED` | `FAILED` |
| 0069 | TIMEOUT_SUCCEEDED | `TIMEOUT` → `APPROVED` on query | `REQUIRES_RECONCILIATION` → `COMPLETED` |
| 0119 | HARD_DECLINE | `DECLINED` (permanent) | `FAILED` |
| 9995 | EXPIRED_CARD | `DECLINED` with `CARD_EXPIRED` | `FAILED` |

---

## 4. Threat Model for Card Data

### 4.1 Logging Leaks

| Risk | Mitigation |
|---|---|
| `LogInterceptor` logs request body of `/virtual-cards/tokenize` | Annotate the tokenize controller method to suppress body logging. Add a Servlet filter that replaces the request body with `"[REDACTED]"` before it reaches any logger. Assert in tests that no `pan`, `cvv`, or `cardNumber` string appears in log output. |
| `GlobalExceptionHandler` includes request body in 400 errors | The existing handler never echoes request bodies; confirm `include-message: never` stays in `application.yml`. |
| Hibernate/JPA SQL logging (already OFF) | N/A — Firestore, no SQL. |
| Firebase Admin SDK debug logging emitting Firestore document values | Set `com.google.cloud.firestore` log level to `WARN` in `logback-spring.xml`. |

### 4.2 Crash Reports

| Risk | Mitigation |
|---|---|
| Flutter crash reporter (Firebase Crashlytics, Sentry) captures screen content | Disable screenshot capture on card screens: `SystemChrome.setEnabledSystemUIMode()` + `FLAG_SECURE` on Android via `FlutterView` settings. |
| Stack trace in crash report includes a PAN string | PAN must never be held in a Dart variable that outlives the `tokenize()` HTTP call. Use `SecureTextField` (never hold value in a controller) or clear immediately after sending. |
| JVM crash dump (hs_err_pid) includes in-memory vault | Add JVM flag `-XX:+ExitOnOutOfMemoryError` to avoid heap dumps in dev; production vault would run in a separate JVM. |

### 4.3 Screenshots and Screen Recording

| Risk | Mitigation |
|---|---|
| OS-level screenshot on card management screen | Set `FLAG_SECURE` on the Activity (Flutter: `FlutterActivity.onWindowFocusChanged` + `setFlags(FLAG_SECURE)`). Covers both screenshots and screen recording. |
| Recent apps thumbnail shows card screen | Same `FLAG_SECURE` flag prevents thumbnail capture. |
| iOS screenshot (no API equivalent) | Mark the card detail view with `.privacySensitive()` modifier (iOS 15+) to blur in screenshots. |
| Auto-fill / password manager screenshots | Do not use `TextInputType.text` for PAN fields; use `TextInputType.number` + `obscureText: true` and disable `autocorrect`, `enableSuggestions`, `toolbarOptions`. |

### 4.4 Clipboard

| Risk | Mitigation |
|---|---|
| User copies card number to clipboard | Disable `copyable` on the `SelectableText` widget used for last4 display. If PAN reveal is ever added, clear clipboard after 30 seconds via `Clipboard.setData(ClipboardData(text: ''))`. |
| System autofill reads clipboard on PAN field | Set `autofillHints: []` (empty) on PAN input field. |

### 4.5 Analytics SDKs

| Risk | Mitigation |
|---|---|
| Firebase Analytics / Amplitude / Mixpanel records screen view with PAN in URL | Never include card data in route parameters or event properties. Screen name logged = `/virtual-cards/detail` (no ID, no last4). |
| Analytics SDK reads TextField content via accessibility tree | Disable semantic labels on PAN fields. Set `Semantics(label: 'Card number input', child: ...)` without exposing the value. |

### 4.6 Backups

| Risk | Mitigation |
|---|---|
| Android Auto Backup captures app data including cached token | Exclude the virtual-card Shared Preferences / secure storage key from backup via `backup_rules.xml`. |
| iOS iCloud backup includes Keychain items | Store card token in `flutter_secure_storage` with `IOSOptions(accessibility: KeychainAccessibility.first_unlock_this_device)` — this excludes item from iCloud backup. |
| Firestore data exported by a rogue admin | Firestore Security Rules lock `vc_virtual_cards` to `allow read, write: if false` (all access via Admin SDK only, never direct client SDK). |

### 4.7 Test Data in Production

| Risk | Mitigation |
|---|---|
| TEST CARD seed data shipped to production Firestore | The vault seed table is loaded only when `app.virtual-card.card-adapter-mode=SIMULATED`. In REAL_GATEWAY mode, `SimulatedCardProvider` bean is not registered; vault seed table is empty. |
| Test card numbers appear in audit logs | The audit log writer must assert `last4.matches("[0-9]{4}")` but never log the full PAN. |
| OTP dev file (`otp-dev.txt`) leaks TEST card issued event | `otp-dev.txt` only shows OTP codes, not card events. No card data is written to that file. |

### 4.8 Brute Force of OTP and Tokenize Endpoint

| Risk | Mitigation |
|---|---|
| Attacker brute-forces the 6-digit card issue OTP | Existing OTP mechanism: 5 attempts → 15-minute lockout (already implemented). Add `VIRTUAL_CARD_ISSUE` to `OtpPurpose`. |
| Attacker submits thousands of PAN guesses to `/tokenize` | Per-user bucket: 3 requests/min. Per-IP bucket: 5 requests/min. After 10 failed tokenize attempts in 24 hours, raise a `FraudAlert` with severity HIGH and require admin review. |
| Attacker uses a valid JWT from a compromised device to tokenize | Step-up PIN required (`X-Step-Up-Pin` header). Stolen JWT alone is not sufficient. |

### 4.9 Token Theft

| Risk | Mitigation |
|---|---|
| Card token stolen from Firestore | Token is AES-256-GCM encrypted at rest. Without `VIRTUAL_CARD_ENCRYPTION_KEY`, the token string is useless. |
| Card token intercepted in transit | TLS 1.3 required end-to-end. Token never appears in URLs (path params only by cardId, never by token). |
| Token exfiltrated from app memory | Token is held only in memory during the transfer flow, never written to local storage, SharedPreferences, or SQLite. |
| Token replay attack for a second transfer | Same idempotency key mechanism as bank transfers: every payment requires a fresh `Idempotency-Key` header. Auth code is single-use on the vault side. |

### 4.10 IDOR on Cards

| Risk | Mitigation |
|---|---|
| User A accesses User B's card via `GET /virtual-cards/{id}` | `requireOwnedCard(cardId, userId)` — returns 403 (not 404) to prevent card-ID enumeration. Same pattern as `requireOwnedAccount`. |
| Admin reads another admin's cards | Admin endpoints for card access must be audited and require a separate ADMIN role check. |
| Attacker enumerates card IDs by trying sequential integers | Card IDs are opaque Firestore-generated IDs (UUID-like), not sequential longs. |

---

## 5. Risks and Recommended Order of Work

### 5.1 Risks

| Risk | Severity | Notes |
|---|---|---|
| PAN accidentally logged | Critical | A single log statement during development can expose card data. Enforce from day 1 with a CI lint rule that rejects any log statement near PAN/CVV fields. |
| `FLAG_SECURE` forgotten on card screens | High | Easy to miss per-screen in Flutter. Apply it globally in `MainActivity` or `AppDelegate` when any card route is active. |
| Vault seed table shipped to production | High | Guard strictly with `@ConditionalOnProperty`. Write a startup assertion: if mode=REAL_GATEWAY and any simulated PAN is in memory, refuse to start. |
| CardProvider registered before vault is ready | High | Use `@ConditionalOnProperty` on `SimulatedCardProvider`; `FutureGatewayCardProvider` is not a bean. |
| Transfer state machine not extended for card-funded path | Medium | The existing state machine handles timeouts correctly. Ensure card auth timeouts also move to `REQUIRES_RECONCILIATION`. |
| Daily limit service not counting card-funded transfers | Medium | `DailyLimitService.checkLimit()` is amount-agnostic. Card-funded transfers must increment the same counter. |
| Mobile PAN field captured by iOS/Android predictive text | Medium | `obscureText: true` + `autocorrect: false` + `enableSuggestions: false` on PAN field. |
| Consent record not written before card issue | Low | Enforced by service layer; validated in tests. |

### 5.2 Recommended Order of Work

```
Step 1 — Vault first (no endpoints yet)
  ├── VirtualCardEncryptor (copy AccountReferenceEncryptor, new key)
  ├── CardVault interface + SimulatedCardVaultImpl (in-memory seed table)
  ├── Unit tests: tokenize accepts seeded PANs, rejects all others
  └── Assert: no PAN appears in any log output in tests

Step 2 — Domain + Store
  ├── VirtualCard record + VirtualCardStatus enum + VirtualCardStateMachine
  ├── VirtualCardStore interface (port)
  ├── FirestoreVirtualCardStore implementation
  └── vc_card_consents collection + VirtualCardConsentRecord

Step 3 — CardProvider layer
  ├── CardProvider interface + CardAuthRequest/Response records
  ├── SimulatedCardProvider (delegates to CardVault)
  ├── FutureGatewayCardProvider stub (not a bean, throws UnsupportedOperationException)
  └── CardProviderRegistry

Step 4 — VirtualCardService + Rate limiter
  ├── CardRateLimiter (copy LookupRateLimiter pattern)
  ├── VirtualCardService: issue, freeze, unfreeze, terminate, list, get
  ├── Consent + audit on every state change
  └── Unit tests: all state transitions, object-level auth, consent required

Step 5 — API endpoints (backend)
  ├── VirtualCardController: 9 endpoints (see §3d)
  ├── SecurityConfig: add /api/v1/virtual-cards/** as authenticated
  ├── OtpPurpose: add VIRTUAL_CARD_ISSUE
  ├── NotificationType: add VIRTUAL_CARD_*
  ├── GlobalExceptionHandler: new card-specific exception mappings
  └── application.yml: new card config keys

Step 6 — Transfer integration
  ├── FundingSource enum + extend InitiateTransferRequest
  ├── TransferService.resolveFundingSource() replacing resolveSenderAccount()
  ├── TransferProcessor: card-funded debit path via CardProvider
  ├── TransactionRecord: add fundingSource + fundingCardId fields
  └── Integration tests: card-funded transfer → all 5 scenarios

Step 7 — Mobile
  ├── VirtualCard domain model + provider (Riverpod)
  ├── Add/manage card screen (PAN input with FLAG_SECURE)
  ├── Card detail screen (last4, expiry, status; no PAN/CVV display)
  ├── Accounts screen: merged bank + card pager
  ├── Send screen: funding source selector (bank vs card)
  └── Widget tests + screenshot tests for FLAG_SECURE

Step 8 — Admin panel
  ├── Admin can view card list per user (no PAN, no token)
  ├── Admin can freeze/terminate any card
  ├── Admin can view card auth log
  └── Audit log entries for admin card actions
```

**Total estimated steps:** 8 feature branches → 8 PRs into main.
Each step is independently testable and deployable.

---

*Generated from codebase scan of `/backend`, `/mobile`, and `/docs`.*
*No code has been written or changed by this document.*
