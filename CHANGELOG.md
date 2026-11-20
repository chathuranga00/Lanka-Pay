# Changelog

All notable changes to the **LankaPay ID** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-10-11

### Added
- **Backend Architecture & Foundation:**
  - Spring Boot 3.3 / Java 21 modular-monolith service architecture.
  - Multi-tenant relational schema managed via automated Flyway migrations (`V1__...` through `V7__...`).
  - Strict modular boundaries: `identity`, `bank`, `registry`, `transfer`, `qr`, `notification`, `audit`, `admin`, and `common`.
- **Identity & Authentication:**
  - Sri Lankan NIC (National Identity Card) validation for both Old (9-digit + suffix) and New (12-digit) formats.
  - Phone number verification with SMS OTP dispatch and rate-limited verification.
  - JWT authentication with 15-minute access tokens and 7-day refresh tokens.
  - BCrypt PIN hashing (cost factor 12) with lockout after 5 consecutive failed attempts.
- **Bank Account Linking & Management:**
  - Account linking with simulated core banking verification.
  - Primary default receiving account selection across multiple connected banks.
  - Per-account inbound receiving discoverability toggles.
  - SHA-256 account masking and AES-256 GCM encrypted account reference storage.
- **Payment Identity Registry (Proxy Resolution):**
  - Phone number proxy resolution to default receiving bank accounts.
  - Privacy-preserving recipient display name masking (e.g., `Nimal P.`) to prevent phone enumeration.
  - Dual-layer Token-Bucket rate limiting (per-user and per-IP) via Bucket4j.
  - Consumer privacy controls: Discoverability toggle and Emergency Identity Freeze.
- **Real-Time Transfer Engine:**
  - Instant P2P transfer orchestration with two-phase settlement simulation.
  - Mandatory client-supplied `Idempotency-Key` header with database-backed deduplication.
  - Daily cumulative spend limit tracking with transactional atomicity.
  - Distinct settlement outcomes: `COMPLETED`, `PENDING` / `REQUIRES_RECONCILIATION`, `FAILED`, and `REVERSED`.
- **Simulated Bank Gateway Adapters:**
  - `SimulatedBankAdapterA`, `SimulatedBankAdapterB`, and `SimulatedBankAdapterC`.
  - Deterministic testing scenarios: Insufficient funds (`100400`), delayed settlement / timeout (`100200`), and transaction reversal (`100500`).
  - `@ConditionalOnProperty` Spring Boot seeder (`SimulatedDataSeeder`) active only in `SIMULATED` mode.
  - Standalone SQL seed script (`seed_simulated_test_data.sql`).
- **QR Code Payments (LankaQR):**
  - Personal static QR generation with embedded routing payload.
  - Dynamic payment request QR generation with amount locks and configurable TTL expiration.
  - Cryptographic digital signing and verification for QR payloads.
- **Transaction History & Audit Receipts:**
  - Paged, multi-criteria filtered transaction history.
  - Downloadable digital receipts featuring SHA-256 tamper-evident integrity checksums.
  - Tamper-evident transaction event journaling in append-only audit repository.
- **Flutter Cross-Platform Mobile Client:**
  - Riverpod state management, GoRouter navigation, and Dio HTTP client with JWT auto-refresh interceptors.
  - Deep emerald green and subtle gold visual theme adhering to Sri Lankan national fintech design guidelines.
  - Multilingual localization support (English, Sinhala, Tamil) via ARB translation bundles.
  - Feature screens: Auth, Home Dashboard, Accounts Management, Send Money flow, LankaQR generation and scanner (`mobile_scanner`), Transaction History, Receipt verification, and Settings.
  - 18 comprehensive widget and mock integration tests.
- **React Admin Web Portal:**
  - React 18, TypeScript, and Vite back-office administrative portal.
  - Dashboard analytics (user counts, volume, bank gateway health).
  - Back-office Reconciliation Queue for resolving delayed / timeout transactions.
  - Bank gateway status toggles (simulate bank outages in real time).
  - Real-time fraud alert monitoring and security audit log search.
- **Automated Testing & Benchmarks:**
  - 318 backend unit, integration, and security regression tests (`SecurityRegressionTest.java`).
  - End-to-End API test suite executing all 8 Section 24 demo steps (`DemoScenarioE2ETest.java`).
  - Multi-threaded load and concurrency benchmark suite (`TransferConcurrencyAndLoadTest.java`) demonstrating 194.53 req/sec throughput and 100% duplicate payment interception.
- **CI/CD Automation:**
  - GitHub Actions workflows for backend Maven verify, Flutter test/analyze, and security scans (Gitleaks + OWASP Dependency Check + npm audit).
- **Comprehensive Documentation:**
  - Detailed `README.md` with visual placeholders, architecture diagrams, and simulation notices.
  - `docs/setup-guide.md`: Step-by-step local developer setup guide.
  - `docs/demo-script.md`: Click-by-click evaluation script for the Section 24 scenario.
  - `docs/api/openapi.json`: Exported OpenAPI 3.0.3 specification JSON.
  - `docs/real-integration-roadmap.md`: Phase 7 regulatory, LankaPay CEFTS/JustPay, and HSM integration roadmap.
  - `docs/known-limitations.md`: Complete accounting of prototype assumptions and boundaries.
  - `docs/load-and-concurrency-results.md`: Response-time and throughput benchmark report.
  - `docs/security-and-privacy.md`: Cryptographic controls, threat modeling, and mitigations.

---

### Security
- Integrated OWASP Dependency-Check Maven plugin with CVSS 8 failure threshold.
- Replaced insecure console logs with PII-masked identifiers.
- Added strict HTTP security headers (HSTS, CSP, X-Frame-Options, X-Content-Type-Options, Referrer-Policy).
- Enforced role-based authorization guards on all admin endpoints.
- Enforced object-level authorization (IDOR protection) on all user account and transfer endpoints.
