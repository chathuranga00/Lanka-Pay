# LankaPay ID — National Payment Identifier & P2P Engine

[![Backend CI](https://github.com/chathuranga00/Lanka-Pay/actions/workflows/backend.yml/badge.svg)](https://github.com/chathuranga00/Lanka-Pay/actions/workflows/backend.yml)
[![Mobile CI](https://github.com/chathuranga00/Lanka-Pay/actions/workflows/mobile.yml/badge.svg)](https://github.com/chathuranga00/Lanka-Pay/actions/workflows/mobile.yml)
[![Security Scan](https://github.com/chathuranga00/Lanka-Pay/actions/workflows/security-scan.yml/badge.svg)](https://github.com/chathuranga00/Lanka-Pay/actions/workflows/security-scan.yml)

> ⚠️ **ACADEMIC PROTOTYPE NOTICE & SIMULATION DISCLAIMER**  
> **All banks, clearing houses, payment cards, and monetary transactions in this project are ENTIRELY SIMULATED.**  
> **NEVER ENTER A REAL CREDIT OR DEBIT CARD NUMBER.** All payment card numbers, CVVs, and expiry dates are simulated test data for academic demonstration only. Use only documented test cards from [docs/test-cards.md](file:///q:/MY%20Projects/Lanka%20Pay/docs/test-cards.md).  
> No real money is transferred, debited, or held at any point. Bank responses, account verification, delayed settlement, and OTP delivery are handled by local simulation adapters labeled `[SIMULATED]` or `[TEST]`. Real banking integration in Sri Lanka requires formal Central Bank of Sri Lanka (CBSL) licensing, LankaPay CEFTS/JustPay network operator agreements, and commercial bank sponsorship. See [Real Integration Roadmap](file:///q:/MY%20Projects/Lanka%20Pay/docs/real-integration-roadmap.md), [Card Security Architecture](file:///q:/MY%20Projects/Lanka%20Pay/docs/card-security.md), and [Known Limitations](file:///q:/MY%20Projects/Lanka%20Pay/docs/known-limitations.md).

---

## Overview

**LankaPay ID** is a full-stack, production-grade prototype for a national proxy payment identifier platform designed for Sri Lanka. Inspired by modern real-time retail payment networks (such as India's UPI, Singapore's PayNow, and Australia's PayID), LankaPay ID decouples sensitive bank account numbers from consumer interactions. Users can link multiple commercial bank accounts to a single verified national payment identity (their mobile phone number), set default receiving accounts, dynamically generate tamper-evident QR codes, and execute instant P2P payments in Sri Lankan Rupees (LKR).

The solution incorporates a hardened Java 21 / Spring Boot 3 modular-monolith backend, a cross-platform Flutter mobile client (iOS and Android), and a React 18 + TypeScript administrative management portal.

---

## Screenshots & Architecture Visuals

```
+----------------------------------------------------------------------------------------------------+
|                                    MOBILE APPLICATION WORKFLOWS                                    |
+------------------------------------+-----------------------------------+---------------------------+
|                                    |                                   |                           |
|       [ Home Dashboard ]           |     [ Recipient Lookup ]          |     [ Transfer Result ]   |
|   - Linked Accounts Summary        |   - Privacy Masking (Nimal P.)    |   - Instant LKR 500.00    |
|   - Quick Actions: Send/Scan/QR    |   - Multi-Bank Account Selector   |   - Bank Reference & Fee  |
|   - Recent Transaction Stream      |   - Discoverability Check         |   - Distinct Status UI    |
|                                    |                                   |                           |
|   *(Screenshot Placeholder)*       |    *(Screenshot Placeholder)*     | *(Screenshot Placeholder)*|
+------------------------------------+-----------------------------------+---------------------------+
|                                    |                                   |                           |
|      [ Dynamic LankaQR ]           |      [ Tamper-Evident Receipt ]   |  [ Admin Recon Queue ]    |
|   - EMVCo / LankaQR format         |   - SHA-256 Event Checksum        |   - 2-Phase Settlement    |
|   - Embedded Amount & Expiry       |   - Downloadable Audit Receipt    |   - Fraud Alert Engine    |
|                                    |                                   |                           |
|   *(Screenshot Placeholder)*       |    *(Screenshot Placeholder)*     | *(Screenshot Placeholder)*|
+------------------------------------+-----------------------------------+---------------------------+
```

---

## Core Features

- **Privacy-Preserving Proxy Identity:** Resolve mobile phone numbers (`+9477XXXXXXX`) to receiving accounts without exposing full 12-to-16 digit bank account numbers. Display names are truncated to initials (e.g., `Nimal P.`) to eliminate brute-force enumeration.
- **Multi-Bank Account Orchestration:** Users can link multiple commercial bank accounts across Bank A, Bank B, and Bank C, set a primary receiving bank, and switch funding accounts instantly per transfer.
- **Deterministic Settlement Lifecycle:** Full support for instant success, hard bank declines, delayed settlement (`REQUIRES_RECONCILIATION`), and administrative reversals.
- **LankaQR Compatibility:** Dynamic and static QR code generation and decoding with embedded digital signatures, amount locks, and configurable TTL expiration.
- **Idempotency & Double-Debit Protection:** Client-supplied `Idempotency-Key` headers enforced with atomic database record reservation, preventing duplicate debits during network timeouts or double-tap interactions.
- **Comprehensive Admin Portal:** Back-office administration for transaction reconciliation, bank gateway health monitoring, role management, fraud alert escalation, and audit logging.
- **Simulated Cards & Vault Tokenization:** Link simulated test debit/credit cards, view masked cards (`•••• 1234`), freeze/unfreeze with step-up PIN verification, and pay from cards in Send/QR flows with challenge OTP verification. *(Note: All cards are strictly simulated; never enter a real card number).*
- **Enterprise Security Hardening:** BCrypt PIN hashing, AES-GCM data-at-rest encryption, Bucket4j token-bucket rate limiting, OWASP Dependency-Check automation, and continuous Gitleaks secret scanning.

---

## Architecture & Tech Stack

```
   ┌────────────────────────────────────────────────────────┐
   │                    Client Tier                         │
   │  ┌───────────────────────┐  ┌───────────────────────┐  │
   │  │  Flutter Mobile App   │  │  React Admin Portal   │  │
   │  │  (iOS / Android)      │  │  (TypeScript / Vite)  │  │
   │  └───────────┬───────────┘  └───────────┬───────────┘  │
   └──────────────┼──────────────────────────┼──────────────┘
                  │ HTTPS / JSON             │ HTTPS / JSON
   ┌──────────────▼──────────────────────────▼──────────────┐
   │           Central API & Orchestrator Tier              │
   │  Spring Boot 3.3 (Java 21 LTS) Modular Monolith        │
   │  - Auth & Phone Verification (JWT + Rate Limiter)      │
   │  - Central Registry & Discovery Engine                 │
   │  - Transfer Processor & Idempotency Filter             │
   │  - LankaQR Engine & Cryptographic Verifier             │
   │  - Admin & Reconciliation Service                      │
   └──────────────┬──────────────────────────┬──────────────┘
                  │                          │
   ┌──────────────▼───────────┐  ┌───────────▼──────────────┐
   │    Persistence Tier      │  │ Simulated Bank Gateways  │
   │  MySQL 8 / Flyway        │  │ - Bank A Gateway (SIM)   │
   │  H2 Test Database        │  │ - Bank B Gateway (SIM)   │
   │  AES-GCM Encrypted Data  │  │ - Bank C Gateway (SIM)   │
   └──────────────────────────┘  └──────────────────────────┘
```

| Layer | Technologies & Frameworks | Key Responsibilities |
| :--- | :--- | :--- |
| **Backend API** | Java 21, Spring Boot 3.3, Spring Security, Spring Data JPA | REST APIs, business validation, JWT auth, transaction orchestration |
| **Database** | MySQL 8.0, Flyway Database Migrations, H2 (Test) | Relational storage, schema versioning, foreign keys, index optimization |
| **Mobile App** | Flutter 3.22+, Dart 3.4+, Riverpod, GoRouter, Dio | Cross-platform client, secure storage, QR scanner, multi-lingual UI |
| **Mobile Design** | Neo-Banking Violet (`#7B5CFA` / `#A592FF`), Material 3, Manrope | Design system tokens centralized in `lib/core/theme/app_tokens.dart` (replaces legacy emerald/gold) |
| **Admin Web** | React 18, TypeScript, Vite, Tailwind CSS, Lucide Icons | Back-office reconciliation, fraud alerts, bank gateway status controls |
| **Security & Quality** | BCrypt, AES-GCM, Bucket4j, OWASP Dependency Check, Gitleaks | Password hashing, rate limiting, vulnerability scanning, secret detection |
| **CI / CD** | GitHub Actions Workflows | Automated Maven verify, Flutter test/analyze, security scans |

---

## Directory Layout

```
lanka-pay/
├── .github/workflows/       # GitHub Actions CI/CD workflows
├── backend/                 # Spring Boot modular monolith backend
│   ├── src/main/java/       # Business modules: auth, bank, transfer, registry, qr, admin
│   ├── src/main/resources/  # application.yml, Flyway migrations, seed scripts
│   └── src/test/java/       # 318 unit, integration, E2E, and concurrency tests
├── mobile/                  # Flutter cross-platform mobile client
│   ├── lib/                 # Feature-first structure (auth, accounts, send, qr, history)
│   └── test/                # Unit, widget, and mock integration test suites
├── admin-web/               # React 18 + TypeScript admin web portal
│   ├── src/                 # Admin dashboard, bank config, recon queue, fraud alerts
│   └── vite.config.ts       # Vite bundler configuration
└── docs/                    # System documentation and compliance specifications
    ├── api/                 # Exported OpenAPI 3.0.3 specification JSON
    ├── setup-guide.md       # Step-by-step local developer setup guide
    ├── demo-script.md       # Complete Section 24 click-by-click evaluation script
    ├── load-and-concurrency-results.md # Performance & load benchmark data
    ├── real-integration-roadmap.md     # CBSL compliance & production roadmap
    ├── known-limitations.md # Documented prototype boundaries
    └── security-and-privacy.md         # Threat model, controls & mitigations
```

---

## Getting Started

To run LankaPay ID locally on your development machine, refer to the detailed instructions in the [Local Setup Guide](file:///q:/MY%20Projects/Lanka%20Pay/docs/setup-guide.md).

### Quick Summary
1. **Backend:**
   ```bash
   cd backend
   ./mvnw clean spring-boot:run
   ```
2. **Mobile App:**
   ```bash
   cd mobile
   flutter pub get
   flutter run --dart-define=API_BASE_URL=http://localhost:8080/api/v1
   ```
3. **Admin Web:**
   ```bash
   cd admin-web
   npm install
   npm run dev
   ```

---

## Demo Credentials (Pre-Seeded)

The application automatically seeds simulated test accounts upon startup in `SIMULATED` mode:

| Persona | Mobile Number | PIN | Description |
| :--- | :--- | :--- | :--- |
| **Kasun Perera** | `+94770000001` | `123456` | Primary sender. Linked to Bank A, Bank B (default), and Bank C. |
| **Nimal Silva** | `+94770000002` | `123456` | Primary receiver. Linked to Bank A. |
| **System Admin** | `+94770000000` | `123456` | Admin user for the React web portal. |

---

## Documentation Index

- [Local Setup Guide](file:///q:/MY%20Projects/Lanka%20Pay/docs/setup-guide.md)
- [Demonstration & Evaluation Script](file:///q:/MY%20Projects/Lanka%20Pay/docs/demo-script.md)
- [OpenAPI Specification](file:///q:/MY%20Projects/Lanka%20Pay/docs/api/openapi.json)
- [Real Integration Roadmap](file:///q:/MY%20Projects/Lanka%20Pay/docs/real-integration-roadmap.md)
- [Known Limitations](file:///q:/MY%20Projects/Lanka%20Pay/docs/known-limitations.md)
- [Load & Concurrency Benchmark Results](file:///q:/MY%20Projects/Lanka%20Pay/docs/load-and-concurrency-results.md)
- [Security & Privacy Architecture](file:///q:/MY%20Projects/Lanka%20Pay/docs/security-and-privacy.md)
- [Changelog](file:///q:/MY%20Projects/Lanka%20Pay/CHANGELOG.md)

---

## Academic Disclaimer & License

This project was developed solely as an academic and technological capstone prototype demonstrating a national digital payment identifier system for Sri Lanka. All third-party trademarks and brand references (such as LankaPay, Dialog, Mobitel, Central Bank of Sri Lanka) are used exclusively for contextual modeling and educational demonstration.
