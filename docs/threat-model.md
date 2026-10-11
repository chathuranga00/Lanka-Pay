# Lanka Pay – Threat Model

> **Draft** – reviewed against project rules in `.cursor/rules/lankapay.mdc`.

Methodology: STRIDE (Spoofing, Tampering, Repudiation, Information Disclosure, Denial of Service, Elevation of Privilege).

---

## STRIDE Threat Table

| # | Threat | STRIDE Category | Attack Scenario | Likelihood | Impact | Mitigations |
|---|---|---|---|---|---|---|
| T-01 | **Phone Number Enumeration** | Information Disclosure | Attacker calls `GET /registry/lookup` repeatedly to harvest which phones are registered | Medium | Medium | Rate-limit lookup endpoint (e.g. 10 req/min per JWT); return identical 404 response for "not found" and "not discoverable"; never distinguish the two cases in the response |
| T-02 | **OTP Brute Force** | Spoofing | Attacker intercepts userId and tries all 000000–999999 combinations against `POST /auth/verify-otp` | High | Critical | Lock account after 5 failed OTP attempts for 15 minutes; rate-limit by IP and userId; OTP is 6 digits (1-in-1,000,000 per attempt); OTP TTL = 5 minutes |
| T-03 | **Replay Attack (Transfer)** | Tampering | Attacker captures a valid `POST /transfers` request and replays it to double-charge the sender | Medium | High | Require a client-generated idempotency key (`Idempotency-Key` header); persist key + result for 24 h; second request returns cached 200 without re-executing |
| T-04 | **IDOR – Access Other Users' Data** | Elevation of Privilege | Authenticated user changes `transferId` or `bankAccountId` in URL to access another user's records | Medium | High | Object-level authorization on every resource endpoint: verify that the resource's `user_id` matches the JWT subject before returning data; return 403 (not 404) to avoid leaking existence |
| T-05 | **Duplicate / Double Payment** | Tampering | Race condition causes two concurrent transfer requests to both succeed, debiting the sender twice | Medium | High | Database unique constraint on `(sender_id, idempotency_key)`; use `SELECT FOR UPDATE` or optimistic locking on `trf_daily_limits`; BankAdapter confirms each leg before the next begins |
| T-06 | **QR Code Tampering** | Tampering | Attacker modifies amount or merchant ID in a QR payload before the payer submits it | Medium | High | Server parses `qrId` from payload and re-fetches canonical amount and merchant from `qr_codes` table; client-provided amount is ignored; QR payload is not trusted input |
| T-07 | **Admin Privilege Abuse** | Elevation of Privilege | Rogue admin freezes accounts, reads all transactions, or exports PII without accountability | Low | Critical | All admin actions write an immutable `aud_audit_log` entry; admin role requires separate credentials; audit log is append-only (no DELETE permission for any app role); periodic log review |
| T-08 | **JWT / Token Theft** | Spoofing | Attacker steals an access token from device storage or network traffic and impersonates the user | Medium | High | Short-lived access tokens (30 min); refresh tokens stored as hashed value server-side; revoke all tokens on password change, PIN change, or account freeze; HTTPS enforced everywhere |
| T-09 | **Account Takeover via Password Reset** | Spoofing | Attacker requests a password-reset OTP for a victim's phone number they control | Medium | Critical | OTP sent to the registered phone only; OTP is hashed before storage; rate-limit reset requests to 3 per hour per phone; log all reset attempts to audit |
| T-10 | **PII Leakage in Logs** | Information Disclosure | Full phone numbers, NICs, or account numbers written to application logs and exposed | Medium | High | Log sanitisation rule in `lankapay.mdc`; never log `phone`, `nic`, `accountNumber`; only log masked versions (`+9477***4567`); tested with a log-scrubbing linter in CI |
| T-11 | **Simulated Bank Treated as Real** | Tampering / Repudiation | Prototype deployed as if payments are real; users believe real money moved | Low | Critical | All simulated responses include `"simulated": true`; UI displays "SIMULATED" banner permanently; `BANK_ADAPTER_MODE=SIMULATED` env var must be explicitly overridden; README and in-app notice |
| T-12 | **Denial of Service on Transfer Endpoint** | Denial of Service | Attacker floods `POST /transfers` to exhaust DB connections or daily-limit rows | Medium | Medium | Rate-limit per user (e.g. 20 transfers/min); enforce daily cap in `trf_daily_limits`; connection pool limits in Spring; circuit breaker on BankAdapter calls |
| T-13 | **Firestore Rules Misconfiguration / Direct Client Access** | Information Disclosure / Tampering / Elevation of Privilege | Client directly accesses Firestore collections (`users`, `linkedBankAccounts`, `transactions`, `idempotencyKeys`, `otpChallenges`, `auditLogs`) using client SDK, bypassing Spring Boot security filters and authorization | Low | Critical | Complete client lockdown in `firestore.rules`: `match /{document=**} { allow read, write: if false; }`. All database operations execute exclusively via Firebase Admin SDK in trusted backend. Verified with `@firebase/rules-unit-testing` regression suite (72 automated tests). |
| T-14 | **Leaked Service Account Key / Elevated Credentials** | Elevation of Privilege / Information Disclosure | GCP service account private key is accidentally committed to version control, granting unauthorized full-admin read/write access to Firestore | Low | Critical | Zero keys committed in git history (verified via git log audit); `.gitignore` blocks all `*.key`, `*.pem`, `*.json`, `*.p12`, `secrets/`; `.env.example` uses empty placeholders; production enforces GCP Workload Identity / IAM metadata (no static keys); continuous Gitleaks secret scanner in CI. |
| T-15 | **Hot-Document Contention & Write Rate Limiting** | Denial of Service | High-volume concurrent writes targeting the same document (e.g., sequential counter, daily limit, or idempotency lock) exceed Firestore's 1 write/sec per document limit, triggering `ABORTED` transaction contention storms | Medium | High | Sharded/distributed ID generation (timestamp-based + UUID) eliminates sequential counter bottlenecks; daily limits partitioned per user per day (`dailyLimits/{userId}_{yyyy-MM-dd}`); in-flight idempotency request coordination in `TransferService` prevents duplicate concurrent execution; exponential backoff in `FirestoreTransactionHelper`. |

---

## Trust Boundaries

```
[ Flutter Client / Web Browser ] ──HTTPS──► [ Spring Security Filter ]
               │                                      │
               │                      ┌───────────────┼──────────────────┐
               │                      │               │                  │
               │                [ USER role ]   [ ADMIN role ]   [ System / Scheduled Jobs ]
               │                      │               │
               │                 Object-level    ROLE_ADMIN check
               │                 authZ check     + audit log
               │                      │
               │                 [ BankAdapter ] ── SIMULATED boundary ──► [ Simulated Bank Stub ]
               │                      │
               │                 [ Firebase Admin SDK ]
               │                      │ (Elevated IAM)
               │                      ▼
               │         [ Google Cloud Firestore ]
               │                      ▲
               └────── Direct Client ─┘
                   Access DENIED
                 (firestore.rules)
```

Key boundaries:
1. **Client → API**: HTTPS + JWT; all input validated and sanitized.
2. **Client → Firestore Direct Boundary**: Completely severed by `firestore.rules` (`match /{document=**} { allow read, write: if false; }`). Mobile and web clients cannot query or modify Firestore directly; all persistence is mediated by the trusted backend.
3. **USER → other users' data**: Object-level authorization checks on every resource query.
4. **USER → ADMIN endpoints**: Strict role check (`ROLE_ADMIN`) with administrative action audit logging.
5. **Application → BankAdapter**: `BankAdapter` interface; simulated stubs are strictly isolated and marked with `simulated: true`.
6. **Application → Audit log**: Append-only; no application code issues DELETE on `auditLogs`.

---

## Assumptions & Out of Scope

- TLS certificate management and network-level DDoS protection are handled by cloud edge / reverse proxy infrastructure.
- Device-level security (jailbreak detection, certificate pinning) is noted but not implemented in the university build.
- The simulated bank stubs do not model real bank fraud controls.
