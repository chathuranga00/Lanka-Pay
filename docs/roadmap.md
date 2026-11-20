# Lanka Pay – Development Roadmap

> **Draft** – phases mapped to the step-based branch workflow used in this repo.
> Each phase produces a mergeable PR on its own `feature/step-XX-*` branch.

---

## Overview

| Phase | Name | Branch Prefix | Key Deliverable |
|---|---|---|---|
| 1 | Project Setup | `step-01` | Repo, rules, docs, folder structure |
| 2 | Backend Foundation | `step-02` | Spring Boot project, DB, Flyway baseline, health endpoint |
| 3 | Identity & Auth | `step-03` | Register, OTP verify, login, JWT, refresh, logout |
| 4 | Registry & Bank Linking | `step-04` | Phone lookup, discovery toggle, link/unlink bank account |
| 5 | Transfers & QR | `step-05` | P2P transfer, daily limits, QR generate & pay |
| 6 | Notifications & Audit | `step-06` | SMS/push (SIMULATED), immutable audit log |
| 7 | Admin & Polish | `step-07` | Admin APIs, Flutter admin web, security hardening, final docs |

---

## Phase 1 – Project Setup (`feature/step-01-project-setup`)

**Goal:** Establish the repository scaffold and all shared conventions before writing any application code.

- [x] `.cursor/rules/lankapay.mdc` – always-on project rules
- [x] `.gitignore`, `.env.example`, `README.md`
- [x] Root folder structure: `/backend`, `/mobile`, `/admin-web`, `/docs`
- [x] Documentation: `architecture.md`, `user-journeys.md`, `er-diagram.md`, `api-spec.md`, `threat-model.md`, `roadmap.md`, `backend-structure.md`

---

## Phase 2 – Backend Foundation (`feature/step-02-backend-foundation`)

**Goal:** A running Spring Boot app connected to MySQL with Flyway migrations and a health-check endpoint.

- [ ] Generate Spring Boot 3 project (Maven, Java 21)
- [ ] Configure `application.yml` (reads from `.env`)
- [ ] Flyway baseline migration `V1__init_schema.sql` (all tables from `er-diagram.md`)
- [ ] `common` module: `Money`, `UserId`, `LankaPayException` hierarchy
- [ ] `GET /actuator/health` returns `{ "status": "UP" }`
- [ ] Docker Compose for local MySQL 8
- [ ] Unit tests for `Money` value object

---

## Phase 3 – Identity & Auth (`feature/step-03-identity-auth`)

**Goal:** Full registration → OTP → login → JWT flow, all endpoints tested.

- [ ] `identity` module: `User`, `Otp`, `RefreshToken` entities + repositories
- [ ] `POST /auth/register` – validation, OTP generation (hashed), SIMULATED SMS
- [ ] `POST /auth/verify-otp` – hash compare, TTL check, JWT issuance
- [ ] `POST /auth/login` – BCrypt verify, audit log
- [ ] `POST /auth/refresh` and `POST /auth/logout`
- [ ] `PATCH /auth/change-pin`
- [ ] OTP rate-limiting (5 attempts → 15 min lockout)
- [ ] Spring Security filter chain configuration
- [ ] Integration tests with `@WebMvcTest` and `@DataJpaTest`

---

## Phase 4 – Registry & Bank Linking (`feature/step-04-registry-bank`)

**Goal:** Phone lookup, privacy controls, and bank account linking via `BankAdapter`.

- [ ] `registry` module: `Registry` entity, `lookupByPhone`, `setDiscoverable`
- [ ] `GET /registry/lookup` with rate-limiting and masked response
- [ ] `PATCH /registry/discovery`
- [ ] `BankAdapter` interface in `bank-integration` module
- [ ] `SimulatedBankAdapter` implementation (always returns success, labels SIMULATED)
- [ ] `POST /bank-accounts`, `GET /bank-accounts`, `DELETE /bank-accounts/{id}`, `PATCH .../default`
- [ ] Audit log entries for all bank-link events
- [ ] Object-level authorization tests

---

## Phase 5 – Transfers & QR (`feature/step-05-transfers-qr`)

**Goal:** Core payment functionality — P2P transfer and QR-triggered payment.

- [ ] `transfer` module: `Transfer`, `DailyLimit` entities
- [ ] `POST /transfers` – full debit/credit via BankAdapter, idempotency key, daily limit
- [ ] `GET /transfers` (paginated history), `GET /transfers/{id}`
- [ ] `qr` module: `QrCode` entity
- [ ] `POST /qr/generate` – 15-minute TTL
- [ ] `POST /qr/pay` – parse, validate, hand off to transfer
- [ ] Server-side QR amount validation (client amount ignored)
- [ ] Duplicate payment prevention (unique idempotency constraint)
- [ ] Integration tests for full transfer and QR pay flows

---

## Phase 6 – Notifications & Audit (`feature/step-06-notifications-audit`)

**Goal:** Decoupled notification delivery and immutable audit trail.

- [ ] `notification` module: `Notification` entity, `NotificationService`
- [ ] SMS and push channels (SIMULATED — prints to log, persists with `simulated=true`)
- [ ] Notification events wired to registration, login, transfer, bank-link
- [ ] `audit` module: `AuditLog` entity, `AuditService` (append-only)
- [ ] Sanitisation of `detail_json` before insert (no PII, no account numbers)
- [ ] Scheduled jobs: expire OTPs, expire QR codes, purge old notifications
- [ ] `GET /admin/audit-log` endpoint

---

## Phase 7 – Admin & Polish (`feature/step-07-admin-polish`)

**Goal:** Admin dashboard, security hardening, documentation finalised.

- [ ] `admin` module: user search, freeze/reactivate, transaction lookup
- [ ] Flutter Web admin dashboard (login, user list, transfer list, audit log view)
- [ ] Flutter mobile app – connect all flows to live API
- [ ] Security hardening: HTTPS config, CORS policy, security headers (`Content-Security-Policy`, `X-Frame-Options`)
- [ ] Rate-limiting verified end-to-end
- [ ] Final documentation pass: update all docs with confirmed details from `MASTER_PROMPT.md`
- [ ] README quick-start verified on a clean machine

---

## Branch / PR Convention

```
feature/step-XX-short-description
         │
         └─── PR → main
              Title: "<type>: short description" (≤ 70 chars)
              e.g.   "feat: identity and auth module"
```

Commit types: `feat`, `fix`, `chore`, `docs`, `test`, `refactor`.
