# LankaPay ID — National Payment Identifier & Simulated Banking Super-App

[![Unified CI Quality Gate](https://github.com/chathuranga00/Lanka-Pay/actions/workflows/ci.yml/badge.svg)](https://github.com/chathuranga00/Lanka-Pay/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3%20(Java%2021)-brightgreen.svg)](https://spring.io/)
[![Flutter](https://img.shields.io/badge/Flutter-3.22+%20(Dart%203.4+)-02569B.svg)](https://flutter.dev/)

> ⚠️ **STATUTORY & REGULATORY DISCLAIMER**  
> **LankaPay ID is an educational and technological simulation.**  
> It is **NOT** an official product of LankaClear (Pvt) Ltd, the Central Bank of Sri Lanka (CBSL), or any commercial bank or financial institution.  
> **ALL BANKS, CLEARING SWITCHES, UTILITY BILLERS, PAYMENT CARDS, LOYALTY POINTS, AND MONETARY TRANSACTIONS ARE 100% SIMULATED.**  
> **NEVER ENTER REAL CREDIT/DEBIT CARDS, REAL BANK ACCOUNTS, OR ACTUAL PASSWORDS/PINS.**  
> No real money is transferred, debited, or held at any point. Bank responses, billing statements, card tokenization, and OTP delivery are handled by local in-memory simulation adapters. Live financial operations in Sri Lanka mandate regulatory licensing under the **Payment and Settlement Systems Act No. 28 of 2005**, formal membership in LankaClear (CEFTS/JustPay/LankaQR), and commercial banking sponsorship. See [Real Integration Roadmap](file:///q:/MY%20Projects/Lanka%20Pay/docs/real-integration-roadmap.md), [Known Limitations](file:///q:/MY%20Projects/Lanka%20Pay/docs/known-limitations.md), and [Threat Model](file:///q:/MY%20Projects/Lanka%20Pay/docs/threat-model.md).

---

## 1. Overview

**LankaPay ID** is a full-stack, enterprise-hardened prototype demonstrating a national proxy payment identifier platform and simulated **neo-banking Super-App** for Sri Lanka. Inspired by modern real-time retail payment networks (such as India's UPI, Singapore's PayNow, and Australia's PayID), LankaPay ID decouples sensitive bank account numbers from consumer interactions while offering a consolidated financial lifestyle experience:

- **Privacy-Preserving Proxy Payment Registry:** Resolve verified mobile numbers (`+9477XXXXXXX`) to bank accounts with display names masked to initials (e.g., `Nimal P.`) to prevent phone directory harvesting.
- **Multi-Bank Orchestration:** Link multiple commercial bank accounts (Bank A, Bank B, Bank C), switch default receiving accounts, and dynamically route funds.
- **EMVCo / LankaQR Engine:** Dynamic and static QR code generation and decoding with embedded cryptographic signatures, minor-unit amounts, and TTL expiration.
- **Consolidated 5-Tab Super-App Experience:**
  1. **Home Tab (`/home`):** Real authenticated user greeting (`Kasun Perera`), interactive Stories carousel, 2-column spending & rewards summary, wallet card with balance, and quick shortcuts.
  2. **Pay Hub (`/pay`):** Unified payments suite (Send P2P, Scan LankaQR, Utility Bills, Split Bill, Scheduled Transfers).
  3. **City Directory (`/city`):** Local merchant discovery, categories (Food, Supermarket, Fuel, Shopping), and LankaQR discount offers.
  4. **Support Chat (`/chat`):** In-app customer support with automated FAQ assistant (`LankaPay Assistant`), simulated human agent handover, and card/PII redaction.
  5. **Explore / Products (`/explore`):** Financial products showcase (Savings Goals, Cards, Family Banking) with strict statutory "Coming Soon" disclosures for regulated investments, credit, and insurance.
- **Resilient Home Aggregator:** Under 500ms SLA with Section Timeout Isolation (`CompletableFuture.allOf`) ensuring partial section failures never crash the home dashboard.
- **Server-Side Feature Toggle Enforcement:** Every endpoint is protected with `@FeatureToggle` HTTP 403 `FEATURE_DISABLED` checks.
- **Minor-Unit Financial Precision:** All monetary amounts are handled strictly as integer minor units (`amountMinor` in Sri Lankan Cents; 1 LKR = 100 cents), preventing IEEE 754 floating-point rounding errors. Loyalty points (`pts`) are strictly separated from fiat currency.

---

## 2. Architecture & Modules Map

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                       LankaPay Client Tier                                              │
│  ┌───────────────────────────────────────────────────────┐  ┌────────────────────────────────────────┐  │
│  │  Flutter 3.22+ Super-App (iOS / Android)              │  │  React 18 Admin Back-Office Portal     │  │
│  │  - 5-Tab Shell: Home, Pay, City, Chat, Explore        │  │  - Reconciliation Queue & Gateways     │  │
│  │  - Riverpod, GoRouter, Dio, AppTokens (Violet Theme)  │  │  - Audit Logs, Fraud Alerts, Toggles    │  │
│  └───────────────────────────┬───────────────────────────┘  └───────────────────┬────────────────────┘  │
└──────────────────────────────┼──────────────────────────────────────────────────┼───────────────────────┘
                               │ HTTPS / REST (X-Correlation-Id, Idempotency-Key) │
┌──────────────────────────────▼──────────────────────────────────────────────────▼───────────────────────┐
│                              Spring Boot 3.3 (Java 21 LTS) Modular Monolith                             │
├──────────────────────────────┬───────────────────────────────┬──────────────────────────────────────────┤
│ Foundational Payment Engine  │ Super-App Value Modules       │ Governance & Security                    │
├──────────────────────────────┼───────────────────────────────┼──────────────────────────────────────────┤
│ • identity (Auth & JWT)      │ • story (Interactive Stories) │ • feature (Dynamic Toggle Interceptor)   │
│ • registry (Proxy & Banks)   │ • bill (Utility Payments)     │ • home (Resilient Multi-Section Aggreg)  │
│ • transfer (P2P Processor)   │ • scheduled (Standing Orders) │ • security (BCrypt, AES-GCM, Rate Limit) │
│ • qr (EMVCo LankaQR Engine)  │ • split (Social Bill Splits)  │ • audit (Append-Only Event Store)        │
│ • card (Simulated Vault)     │ • rewards (Loyalty Ledger)    │ • admin (Recon & Gateway Controls)       │
│                              │ • city (Merchant Directory)   │                                          │
│                              │ • support (Chat Assistant)    │                                          │
│                              │ • goals (Savings Goals)       │                                          │
└──────────────────────────────┴───────────────────────────────┴──────────────────────────────────────────┘
```

---

## 3. Pre-Seeded Personas & Demo Credentials

The backend automatically seeds deterministic personas upon startup in `SIMULATED` mode:

| Persona | Mobile Number | PIN | Primary Roles & Accounts |
| :--- | :--- | :--- | :--- |
| **Kasun Perera** | `+94770000001` | `123456` | Primary Super-App user. Linked to Bank A, Bank B (default), Bank C, test cards, active bills, loyalty points. |
| **Nimal Silva** | `+94770000002` | `123456` | Counterparty recipient. Linked to Bank A. Receives P2P transfers & split requests. |
| **Dilshan Mendis** | `+94770000003` | `123456` | Participant for group bill splitting and P2P transfers. |
| **System Admin** | `+94770000000` | `123456` | Back-office administrator for React portal (`ROLE_ADMIN`). |

---

## 4. 12-Step Super-App Demonstration Scenario

The entire system is validated by an automated end-to-end integration test (`SuperAppEndToEndDemoTest.java`) and can be executed click-by-click using [docs/demo-script.md](file:///q:/MY%20Projects/Lanka%20Pay/docs/demo-script.md):

1. **Step 1: Authenticate Kasun Perera** (`POST /api/v1/auth/login`) ➔ Receives JWT and user profile.
2. **Step 2: Load Home Dashboard** (`GET /api/v1/home`) ➔ User greeting `Kasun Perera`, wallet balance, stories carousel, spending & rewards summary.
3. **Step 3: View Interactive Story** (`GET /api/v1/stories`, `POST /api/v1/stories/{id}/view`) ➔ Slide opens with validated internal deep link.
4. **Step 4: Execute P2P Transfer** (`POST /api/v1/transfers`) ➔ Instant transfer of LKR 500.00 (`50000` cents) to Nimal Silva (`+94770000002`).
5. **Step 5: Verify Loyalty Points Accrual** (`GET /api/v1/rewards/summary`) ➔ Points earned on successful transfer; points ledger incremented.
6. **Step 6: Inquire & Pay Utility Bill** (`POST /api/v1/bills/inquire`, `POST /api/v1/bills/pay`) ➔ Look up electricity bill (`CEB-0001`, LKR 3,450.00) and pay with idempotency.
7. **Step 7: Create Scheduled Standing Order** (`POST /api/v1/scheduled`) ➔ Set up monthly standing order for rent of LKR 25,000.00 (`2500000` cents).
8. **Step 8: Split Dinner Bill** (`POST /api/v1/split/groups`, `POST /api/v1/split/expenses`) ➔ Split LKR 6,000.00 bill equally among 3 friends (LKR 2,000.00 each).
9. **Step 9: Explore City Merchants** (`GET /api/v1/city/merchants`) ➔ Discover local LankaQR merchants (Food, Fuel, Supermarkets) and claim discount vouchers.
10. **Step 10: Customer Support Chat** (`POST /api/v1/support/conversations`, `POST /api/v1/support/messages`) ➔ Ask `LankaPay Assistant` questions; verify card numbers/PANs are masked automatically.
11. **Step 11: Explore Financial Products** (`/explore`) ➔ Verify statutory "Coming Soon" disclosures for Invest, Borrow, and Insurance.
12. **Step 12: Feature Flag Disablement Verification** (`POST /api/v1/admin/features/bills/disable`) ➔ Toggling bill payments returns HTTP 403 `FEATURE_DISABLED` instantly.

---

## 5. Automated Test Suites & Quality Gates

The codebase is protected by strict quality gates across backend, mobile client, and security scanning:

### Backend Test Verification (Java 21 / Maven)
```bash
cd backend
./mvnw clean test
```
- **Total Tests:** 318+ tests covering domain units, mock integrations, concurrency, and security.
- **Key Test Suites:**
  - `SuperAppEndToEndDemoTest`: Validates the full 12-step demo scenario sequentially.
  - `FeatureToggleSecurityTest`: Verifies HTTP 403 `FEATURE_DISABLED` enforcement across protected endpoints.
  - `HomeAggregatorPerformanceTest`: Validates < 500ms Home aggregator SLA under concurrent load with section failure isolation.
  - `ScheduledModuleTest`: Validates standing orders, cron execution, failure tracking, and pause/resume logic.
  - `AuthControllerTest`: Validates phone authentication, rate limiting, and role-based access control.

### Mobile Test Verification (Flutter 3.22+)
```bash
cd mobile
flutter test
```
- **Total Tests:** **229 passed, 0 failed.**
- **Key Test Suites:**
  - `screens_golden_test.dart`: 14 golden visual regression tests for Home, Pay Hub (`/pay-hub`), City (`/city`), Chat (`/support`), Explore (`/explore`), Accounts (`/accounts`), and Send (`/send`) in both **Light and Dark** modes.
  - `localization_test.dart`: Exhaustive dictionary and widget verification across all 3 supported languages (**English `en`**, **Sinhala `si`**, and **Tamil `ta`**).
  - `all_keys_tap_test.dart`: Automated key traversal verifying zero unhandled navigation taps or unmocked timer exceptions.

### Unified CI Pipeline (`.github/workflows/ci.yml`)
The unified GitHub Actions workflow runs three parallel quality gates on every push and pull request:
1. **`backend-verify`**: Java 21 LTS, Maven clean verify, OWASP Dependency-Check.
2. **`mobile-verify`**: Flutter 3.22+, `flutter analyze --fatal-infos`, `flutter test`, golden visual checks, localization audits.
3. **`security-gitleaks`**: Gitleaks secret detection, LUHN card scan script (`scripts/scan-luhn-cards.py`).

---

## 6. Local Quick Start

To run the complete LankaPay Super-App locally:

### Prerequisites
- Java 21 LTS (OpenJDK / Eclipse Temurin)
- Flutter SDK 3.22+ & Dart 3.4+
- Node.js 18+ (for Admin Web)
- MySQL 8.0 (optional; H2 runs in-memory by default)

### 1. Start Backend API
```bash
cd backend
./mvnw clean spring-boot:run
```
*API will start on `http://localhost:8080` (Swagger UI: `http://localhost:8080/swagger-ui.html`).*

### 2. Start Mobile Client
```bash
cd mobile
flutter pub get
flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8080/api/v1
```
*(Or target your connected iOS simulator / Android emulator).*

### 3. Start Admin Web Portal
```bash
cd admin-web
npm install
npm run dev
```
*Admin portal will open at `http://localhost:5173`.*

---

## 7. Documentation Index

- [Super-App Plan & Specifications](file:///q:/MY%20Projects/Lanka%20Pay/docs/superapp-plan.md) — Architecture and functional specs for all 11 modules.
- [Architecture Documentation](file:///q:/MY%20Projects/Lanka%20Pay/docs/architecture.md) — System boundaries, aggregator resilience, and Mermaid component diagrams.
- [Data Model & Schemas](file:///q:/MY%20Projects/Lanka%20Pay/docs/data-model.md) — Minor-unit financial precision, append-only ledgers, and Firestore/JPA entities.
- [REST API Specification](file:///q:/MY%20Projects/Lanka%20Pay/docs/api-spec.md) — Complete endpoint catalog, error models, and global request headers.
- [Threat Model (STRIDE)](file:///q:/MY%20Projects/Lanka%20Pay/docs/threat-model.md) — Security analysis across T-01 through T-29.
- [Security & Privacy Controls](file:///q:/MY%20Projects/Lanka%20Pay/docs/security-and-privacy.md) — Findings SEC-01 through SEC-14 and mitigation verification.
- [Demonstration & Evaluation Script](file:///q:/MY%20Projects/Lanka%20Pay/docs/demo-script.md) — 12-step click-by-click evaluation guide.
- [Local Developer Setup Guide](file:///q:/MY%20Projects/Lanka%20Pay/docs/setup-guide.md) — Prerequisites, environment variables, and troubleshooting.
- [Known Limitations & Prototype Boundaries](file:///q:/MY%20Projects/Lanka%20Pay/docs/known-limitations.md) — Operational boundaries and trade-offs.
- [Real Integration & CBSL Roadmap](file:///q:/MY%20Projects/Lanka%20Pay/docs/real-integration-roadmap.md) — Regulatory requirements, ISO 8583/20022 protocols, and PCI-DSS Level 1 certification.

---

## 8. License

This repository is distributed under the [MIT License](LICENSE).  
All brand and institutional references (LankaPay, Central Bank of Sri Lanka, commercial banks) are used strictly for contextual modeling and educational demonstration.
