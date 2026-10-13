# LankaPay ID — Simulated Banking Super App Architecture & Migration Plan

> ⚠️ **ACADEMIC PROTOTYPE NOTICE & ARCHITECTURAL DISCLAIMER**  
> All banking integrations, billers, card vaults, loyalty programs, and financial accounts in this project are **strictly simulated** for educational and architectural demonstration.  
> **No real money is transferred, held, or debited at any point.**  
> Real banking and fintech operations in Sri Lanka mandate regulatory licensing from the **Central Bank of Sri Lanka (CBSL)**, formal operator integration with **LankaPay (Pvt) Ltd (CEFTS/JustPay/LankaQR)**, and commercial bank sponsorship. See [Real Integration Roadmap](file:///q:/MY%20Projects/Lanka%20Pay/docs/real-integration-roadmap.md).

---

## Executive Summary & Target Vision

This document details the transformation of **LankaPay ID** from a focused national payment proxy registry into a full-featured, **simulated neo-banking "Super App"**.

### Visual & Structural Inspiration
Based on modern neo-banking mobile design benchmarks (referencing the structural layout in target UI mockups):
1. **Home Tab:** Features a personalized greeting with the **authenticated user's real registered name** (`Kasun Perera`), initial avatar badge (`[K]`), rewards icon, global search bar, horizontal interactive story cards, a 2-column spending & rewards summary, primary wallet card with simulated balance, prioritized nudge banner, and quick-action shortcuts.
2. **Products / Explore Tab:** Features a primary "Get a card" banner, a 2x2 grid of financial products (**Invest**, **Borrow**, **Save**, **Insurance**), and a **Family Banking** overview.
3. **Consolidated 5-Tab Navigation Shell:**
   - **Tab 0: Home** (Dashboard, Stories, Wallet, Spend Summary, Nudge)
   - **Tab 1: Pay** (Consolidated payments hub: Send, Request, LankaQR, Pay Bills, Split Bill, Scheduled Transfers)
   - **Tab 2: City** (Merchant directory, LankaQR places, discount vouchers, category discovery)
   - **Tab 3: Chat** (Customer support chat with automated FAQ assistant + live support simulation)
   - **Tab 4: Explore** (Financial products showcase: Cards, Savings Goals, Partner Notices)

> [!IMPORTANT]
> **User Name Invariant:** The home screen must display the authenticated user's real registered name (e.g. `Kasun Perera` from user profile / auth token) and initial (`K`), never hardcoded mock names like "Mikhail".

---

## 1. Existing System Architecture & Patterns Baseline

To ensure seamless architectural continuity across the modular monolith, all new modules must strictly adhere to the patterns established in `/backend`, `/mobile`, and `/docs`:

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                 LankaPay Modular Monolith                                │
├─────────────────────────┬─────────────────────────┬─────────────────────────────────────┤
│      Domain Layer       │      Service Layer      │          Store / SPI Layer          │
├─────────────────────────┼─────────────────────────┼─────────────────────────────────────┤
│ - Immutable Records/DTOs│ - Idempotency Filter    │ - Firestore Document Stores         │
│ - Transaction State Mach│ - Step-Up PIN Auth      │ - H2/MySQL Test Databases           │
│ - Minor-Unit Currency   │ - Daily Risk Limits     │ - Pluggable BankAdapter SPI         │
│ - Append-Only Events    │ - Bucket4j Rate Limiter │ - Pluggable CardProvider SPI        │
└─────────────────────────┴─────────────────────────┴─────────────────────────────────────┘
```

### 1.1 Persistence Approach
- **Store Abstraction Pattern:** Domain services interact solely with interface abstractions (e.g. `TransactionStore`, `BankAccountStore`, `CardStore`, `UserStore`).
- **Firestore Document Engine:** Production storage runs on Google Cloud Firestore (`com.lankapay.<module>.store.firestore`).
  - Collections: `users`, `bank_accounts`, `cards`, `transactions`, `audit_logs`, `notifications`, `idempotency_records`.
  - Subcollections/Maps: Nested events and usage buckets.
  - Test Harness: Spring Data H2/in-memory implementations (`@Profile("test")`) ensure rapid, hermetic test execution without cloud dependencies.
- **Soft-Delete Convention:** Financial and profile entities are never physically deleted; states transition to `DELETED` or `CLOSED` to preserve auditability.

### 1.2 Module Structure Convention
Each module resides in its dedicated package under `com.lankapay.<module>`:
```
com.lankapay.<module>/
├── domain/       # Aggregates, immutable models, state machines, enums
├── service/      # Transactional business logic, validators, orchestrators
├── store/        # Storage SPI interfaces and .firestore implementations
├── web/          # Spring @RestController endpoints
│   └── dto/      # Request & Response DTOs with @Valid and masked toString()
├── adapter/      # External SPI interface (if interacting with external systems)
│   └── simulated/# In-memory deterministic simulation adapters
└── exception/    # Domain-specific exceptions mapped in GlobalExceptionHandler
```

### 1.3 Established Architectural Patterns
1. **Idempotency Pattern:**
   - Every state-mutating transaction requires an `Idempotency-Key` header.
   - Enforced by `IdempotencyService`: reserves the key atomically before processing, tracks state (`IN_PROGRESS`), and caches the completed response for seamless replay on client retry or network timeout.
2. **Transaction State Machine:**
   - Explicit transitions defined in `TransactionStateMachine`:
     `QUOTED` ➔ `PENDING` ➔ `PROCESSING` ➔ `COMPLETED` / `FAILED` / `REQUIRES_RECONCILIATION`.
   - **Invariants:**
     - Timeouts **never** mark transactions as `FAILED`; they transition to `REQUIRES_RECONCILIATION`.
     - Responses **never** say `SUCCESS` until confirmed by the downstream adapter.
3. **Audit Logging:**
   - Append-only records captured via `AuditService` in `audit_logs`: records `actorId`, `action`, `resourceType`, `resourceId`, `ipAddress`, `timestamp`, and sanitized details.
4. **Notifications:**
   - Multi-channel delivery via `NotificationService` (SMS, Email, Push). Template-driven with privacy masking (account numbers masked to last 4 digits, PANs never included).
5. **Risk Limits & Velocity:**
   - Minor-unit daily tracking via `CardDailyLimitService` and `DailyLimitRecord`. Consecutive decline counter flags potential fraud.
6. **Step-Up Authentication:**
   - High-privilege actions (card unfreeze, card deletion, sensitive data reveal, high-value transfer) require the `X-Step-Up-Pin` request header. Validated against BCrypt-hashed PINs with attempt lockout.
7. **Rate Limiting:**
   - Token bucket algorithm powered by Bucket4j (`RateLimitFilter`, `VaultRateLimiter`), enforcing limits per IP and per authenticated user.
8. **Adapter Pattern (SPI):**
   - Business services depend strictly on interfaces (`BankAdapter`, `CardProvider`). Concrete implementations (`SimulatedBankAAdapter`, `SimulatedCardVaultImpl`) are swapped via configuration.

---

## 2. Modular-Monolith Design for NEW Backend Modules

All new modules will be implemented within the existing Spring Boot modular monolith, preserving existing API contracts while providing modular boundaries.

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                   New Backend Modules Map                                        │
├─────────────┬─────────────┬─────────────┬─────────────┬─────────────┬──────────────┬─────────────┤
│   stories   │    bills    │  scheduled  │    split    │  analytics  │   rewards    │    city     │
├─────────────┼─────────────┼─────────────┼─────────────┼─────────────┼──────────────┼─────────────┤
│   support   │  insights   │    goals    │   family    │   feature   │     home     │             │
│   (chat)    │  (nudges)   │  (savings)  │ (pockets)   │   (flags)   │ (aggregator) │             │
└─────────────┴─────────────┴─────────────┴─────────────┴─────────────┴──────────────┴─────────────┘
```

### 2.1 Module Specifications

#### 1. Stories Module (`com.lankapay.story`)
- **Purpose:** Interactive, educational, and promotional story cards rendered in the top horizontal carousel of the Home screen (e.g. "Tailored to you", "Read tips & insights", "Palmira Business Club").
- **Data Model:**
  - `Story` (id, title, category, coverGradient, thumbnailUrl, unreadByDefault, priority, expiresAt, status)
  - `StorySlide` (id, storyId, orderIndex, title, subtitle, imageUrl, ctaText, ctaDeepLink)
  - `StoryUserView` (userId, storyId, viewedAt)
- **Endpoints:**
  - `GET /api/v1/stories` — Retrieve active stories for the user (with `viewed` boolean flag)
  - `GET /api/v1/stories/{id}` — Retrieve full slide details for a specific story
  - `POST /api/v1/stories/{id}/view` — Mark a story as viewed by the authenticated user
- **Adapter Interface:** Internal catalog; no external adapter required.
- **Tests:** `StoryServiceTest`, `StoryControllerTest`.

#### 2. Bills Module (`com.lankapay.bill`)
- **Purpose:** Utility bill inquiries and bill settlement (Electricity, Water, Telecom, Municipal Councils).
- **Data Model:**
  - `Biller` (id, code, name, category, logoUrl, accountRegex, isSimulated)
  - `BillInquiry` (inquiryId, billerCode, accountNumber, customerName, amountDueMinor, dueDate, status)
  - `BillPayment` (paymentId, billerCode, accountNumber, amountMinor, feeMinor, transferId, receiptRef, status)
- **Adapter Interface:**
  - `BillerAdapter` — Interface: `inquireBill(BillerInquiryRequest)`, `payBill(BillerPaymentRequest)`, `reconcileBill(BillerReconRequest)`.
  - `SimulatedBillerAdapter` — Pre-seeded test account rules:
    - Account ends with `0001`: Due LKR 3,450.00 (`ALWAYS_PAYABLE`)
    - Account ends with `0002`: Due LKR 12,800.00 (`OVERDUE`)
    - Account ends with `0009`: Error `BILLER_OFFLINE`
- **Endpoints:**
  - `GET /api/v1/bills/categories` — List available bill categories
  - `GET /api/v1/bills/billers?category={cat}` — List billers in a category
  - `POST /api/v1/bills/inquire` — Look up outstanding balance for an account
  - `POST /api/v1/bills/pay` — Execute bill payment (requires `Idempotency-Key` and `X-Step-Up-Pin` for > LKR 10,000)
  - `GET /api/v1/bills/history` — List user's bill payment history
- **Tests:** `BillServiceTest`, `SimulatedBillerAdapterTest`, `BillControllerTest`.

#### 3. Scheduled Payments Module (`com.lankapay.scheduled`)
- **Purpose:** Standing orders and recurring P2P transfers / bill payments (daily, weekly, monthly).
- **Data Model:**
  - `ScheduledPayment` (id, userId, title, fundingSourceId, recipientProxy, amountMinor, frequency, nextExecutionDate, endDate, failureCount, status)
  - `ScheduledExecutionLog` (id, scheduledPaymentId, executionDate, status, transferId, errorMessage)
- **Execution Engine:** Background cron scheduler (`@Scheduled`) processes due payments through `TransferProcessor` with unique idempotency keys (`SCHED-{id}-{date}`).
- **Endpoints:**
  - `GET /api/v1/scheduled` — List active scheduled payments
  - `POST /api/v1/scheduled` — Create a new recurring schedule
  - `GET /api/v1/scheduled/{id}` — View schedule details and execution log
  - `PUT /api/v1/scheduled/{id}/pause` — Pause a scheduled payment
  - `PUT /api/v1/scheduled/{id}/resume` — Resume a scheduled payment
  - `DELETE /api/v1/scheduled/{id}` — Cancel a scheduled payment
- **Adapter Interface:** Internal orchestrator; invokes `TransferProcessor`.
- **Tests:** `ScheduledPaymentServiceTest`, `ScheduledExecutionEngineTest`.

#### 4. Split Bills Module (`com.lankapay.split`)
- **Purpose:** Group bill splitting among contacts/proxies with tracking of settled shares.
- **Data Model:**
  - `SplitBill` (id, creatorUserId, title, totalAmountMinor, currency, splitType [EQUAL|EXACT], status, createdAt)
  - `SplitParticipant` (id, splitBillId, userPhone, displayName, shareAmountMinor, hasSettled, settledTransferId, settledAt)
- **Endpoints:**
  - `POST /api/v1/split/bills` — Create a new split bill and send notifications
  - `GET /api/v1/split/bills/{id}` — View split bill progress and participant status
  - `POST /api/v1/split/bills/{id}/settle` — Settle user's own share via instant transfer
  - `POST /api/v1/split/bills/{id}/remind` — Send a reminder push/SMS to unsettled participants
  - `GET /api/v1/split/my-splits` — List user's outgoing and incoming splits
- **Adapter Interface:** Internal orchestrator; coordinates with `NotificationService` and `TransferProcessor`.
- **Tests:** `SplitBillServiceTest`, `SplitControllerTest`.

#### 5. Analytics & Budgeting Module (`com.lankapay.analytics`)
- **Purpose:** Transaction categorization, monthly spend tracking against budgets, and downloadable statement generation.
- **Data Model:**
  - `CategorySpend` (category [FOOD, UTILITIES, SHOPPING, TRANSPORT, HEALTH, OTHER], totalMinor, percentage, transactionCount)
  - `MonthlyBudget` (userId, yearMonth, budgetLimitMinor, alertThresholdPercent, currentSpendMinor, status)
  - `StatementExport` (exportId, userId, fromDate, toDate, format [PDF|CSV], downloadUrl, generatedAt)
- **Adapter Interface:**
  - `StatementGeneratorAdapter` + `SimulatedStatementGeneratorAdapter` for PDF/CSV generation.
- **Endpoints:**
  - `GET /api/v1/analytics/summary` — Monthly spend summary, comparison with previous month
  - `GET /api/v1/analytics/categories` — Breakdown of expenses by category
  - `GET /api/v1/analytics/budget` — Current month's budget and progress
  - `PUT /api/v1/analytics/budget` — Set or update monthly budget limit
  - `POST /api/v1/analytics/statements/export` — Request statement PDF/CSV download
- **Tests:** `AnalyticsServiceTest`, `MonthlyBudgetTest`.

#### 6. Rewards Module (`com.lankapay.rewards`)
- **Purpose:** Gamification points and loyalty badges (points != money).
- **Data Model:**
  - `RewardAccount` (userId, currentPointsBalance, lifetimePointsEarned, tier [BRONZE, SILVER, GOLD, PLATINUM], updatedAt)
  - `RewardLedgerEntry` (id, userId, pointsDelta, reasonCode, referenceType, referenceId, createdAt, runningBalance)
  - `RewardVoucher` (id, voucherCode, partnerName, title, pointsCost, discountPercent, expiresAt, status)
- **Invariants:**
  - **Points are NOT money**: Points cannot be redeemed for cash or deposited into bank accounts.
  - **Append-Only Ledger**: All balance modifications occur strictly via immutable `RewardLedgerEntry` inserts.
- **Endpoints:**
  - `GET /api/v1/rewards/balance` — View user's points balance and tier status
  - `GET /api/v1/rewards/ledger` — View chronological points transaction history
  - `GET /api/v1/rewards/catalog` — View available reward partner vouchers
  - `POST /api/v1/rewards/redeem` — Redeem points for a partner voucher
- **Tests:** `RewardLedgerServiceTest` (verifying append-only invariant), `RewardTierTest`.

#### 7. City Module (`com.lankapay.city`)
- **Purpose:** Directory of local merchants accepting LankaQR, exclusive discount offers, and partner store locators.
- **Data Model:**
  - `Merchant` (id, name, legalName, category, logoUrl, lankaQrPayload, rating, address, latitude, longitude, isVerified)
  - `MerchantOffer` (id, merchantId, title, description, discountTag, validUntil, termsUrl)
- **Adapter Interface:**
  - `GeoMerchantAdapter` + `SimulatedGeoMerchantAdapter` providing spatial bounding-box search.
- **Endpoints:**
  - `GET /api/v1/city/merchants` — List/search merchants with category and query filters
  - `GET /api/v1/city/merchants/{id}` — View merchant profile and active offers
  - `GET /api/v1/city/offers` — List top trending discount offers across all city merchants
  - `GET /api/v1/city/categories` — List merchant categories (Food, Supermarket, Fuel, Shopping, Health)
- **Tests:** `CityMerchantServiceTest`, `CityControllerTest`.

#### 8. Support Module (`com.lankapay.support`)
- **Purpose:** In-app customer support chat with an automated FAQ bot assistant and simulated human agent fallback.
- **Data Model:**
  - `SupportThread` (id, userId, subject, status [OPEN, PENDING_AGENT, RESOLVED], createdAt, updatedAt)
  - `SupportMessage` (id, threadId, senderType [USER, BOT, AGENT], senderName, content, attachmentUrl, createdAt)
- **Adapter Interface:**
  - `SupportBotAdapter` + `SimulatedSupportBotAdapter`:
    - Responds automatically to standard Sri Lankan banking FAQs (LankaQR limits, CEFTS settlement delays, card freezing, OTP troubleshooting).
    - Detects keywords requiring escalation (e.g., "fraud", "unauthorized", "lost phone") and transitions thread status to `PENDING_AGENT`.
- **Endpoints:**
  - `GET /api/v1/support/threads` — List user's support threads
  - `POST /api/v1/support/threads` — Start a new support thread
  - `GET /api/v1/support/threads/{id}/messages` — Retrieve conversation history
  - `POST /api/v1/support/threads/{id}/messages` — Send a message in a thread
- **Tests:** `SupportChatServiceTest`, `SimulatedSupportBotAdapterTest`.

#### 9. Insights Module (`com.lankapay.insights`)
- **Purpose:** Rule-based proactive financial tips and contextual nudges.
- **Data Model:**
  - `InsightItem` (id, userId, type [BUDGET_WARNING, RECURRING_DUE, SAVINGS_TIP, SECURITY_NOTICE], title, body, actionLabel, actionUrl, priority, isDismissed, createdAt)
- **Endpoints:**
  - `GET /api/v1/insights` — Retrieve active insights for the user
  - `POST /api/v1/insights/{id}/dismiss` — Dismiss an insight
- **Tests:** `InsightEngineTest`.

#### 10. Goals Module (`com.lankapay.goals`)
- **Purpose:** Simulated savings goals and sub-account piggy banks (e.g. "Emergency Fund", "New Laptop").
- **Data Model:**
  - `SavingsGoal` (id, userId, title, targetAmountMinor, currentSavedMinor, targetDate, linkedAccountId, status [ACTIVE, COMPLETED, CANCELLED], isSimulated)
  - `GoalTransaction` (id, goalId, amountMinor, direction [DEPOSIT|WITHDRAW], createdAt)
- **Invariants:**
  - Goals represent simulated sub-allocations within linked accounts. All balances are explicitly labelled `SIMULATED`.
- **Endpoints:**
  - `GET /api/v1/goals` — List user's savings goals
  - `POST /api/v1/goals` — Create a new savings goal
  - `GET /api/v1/goals/{id}` — View goal progress and deposit history
  - `POST /api/v1/goals/{id}/deposit` — Transfer simulated funds into goal
  - `POST /api/v1/goals/{id}/withdraw` — Withdraw funds from goal back to primary account
  - `DELETE /api/v1/goals/{id}` — Close goal
- **Tests:** `SavingsGoalServiceTest`.

#### 11. Family Banking Module (`com.lankapay.family` — Optional, Last Phase)
- **Purpose:** Dependent allowances and supervised pockets.
- **Data Model:**
  - `FamilyPocket` (id, parentUserId, dependentName, relationship, monthlyLimitMinor, currentSpendMinor, status)
- **Endpoints:**
  - `GET /api/v1/family` — List family pockets
  - `POST /api/v1/family` — Create a family pocket
  - `PUT /api/v1/family/{id}/limit` — Adjust allowance limit (requires step-up PIN)
- **Tests:** `FamilyBankingServiceTest`.

---

## 3. Feature-Flag System Architecture

A centralized feature-flag engine guarantees that new modules can be rolled out safely or disabled dynamically per user, environment, or regulatory requirement.

```
┌────────────────────────────────────────────────────────┐
│               GET /api/v1/features                     │
└──────────────────────────┬─────────────────────────────┘
                           │
      ┌────────────────────┼────────────────────┐
      ▼                    ▼                    ▼
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│ Application │      │ Spring MVC  │      │ Flutter     │
│   Config    │      │ Interceptor │      │ Route Guard │
│  (YAML/Env) │      │  (Backend)  │      │  (Mobile)   │
└─────────────┘      └─────────────┘      └─────────────┘
```

### 3.1 Endpoint Contract
`GET /api/v1/features` (Available to authenticated users):

```json
{
  "features": {
    "stories": true,
    "bills": true,
    "scheduled": true,
    "split": true,
    "analytics": true,
    "rewards": true,
    "city": true,
    "support": true,
    "insights": true,
    "goals": true,
    "family": false,
    "investments": false,
    "borrowing": false,
    "insurance": false
  },
  "environment": "SANDBOX_SIMULATED",
  "disclaimer": "All modules operate under simulated financial data. Non-payment banking services require licensed institutional partners."
}
```

### 3.2 Backend Enforcement
- **Annotation:** `@FeatureToggle("bills")` applied at controller or method level.
- **HandlerInterceptor:** `FeatureToggleInterceptor` checks the requested feature against `FeatureFlagService`.
- If disabled, the API immediately halts execution and returns:
  ```json
  {
    "status": 403,
    "errorCode": "FEATURE_DISABLED",
    "message": "The bills module is currently disabled in this environment.",
    "correlationId": "corr-fe4a-1123"
  }
  ```

### 3.3 Mobile Enforcement
- On application boot and user login, the Flutter app queries `featuresProvider` via `GET /api/v1/features`.
- Navigation tabs and home screen shortcut tiles inspect flags before rendering:
  - If `bills` is `false`, the "Pay Bills" shortcut tile is automatically hidden.
  - If `city` is `false`, the City bottom navigation tab is disabled or replaced with an informational banner.
  - If a user manually deep-links to a disabled route, `AppRouter` redirects to `/home` with an alert message.

---

## 4. Resilient Home Aggregator: `GET /api/v1/home`

To achieve the snappy, native feel seen in the target UI screenshot without cascading network requests, a dedicated **Home Aggregator** combines all essential dashboard components into a single API response.

### 4.1 Section Isolation Invariant
> [!CRITICAL]
> **Zero Cascading Failures:** Every section in the `GET /api/v1/home` response is wrapped in an independent `HomeSection<T>` container containing a `status` marker (`OK` or `FAILED`) and nullable `error` code. A database glitch in rewards or a timeout in analytics **must never blank or crash the Home screen**.

```json
{
  "greeting": {
    "status": "OK",
    "data": {
      "fullName": "Kasun Perera",
      "initial": "K",
      "salutation": "Good morning, Kasun"
    },
    "error": null
  },
  "unreadNotifications": {
    "status": "OK",
    "data": {
      "count": 3
    },
    "error": null
  },
  "rewardsPoints": {
    "status": "OK",
    "data": {
      "pointsBalance": 1450,
      "tier": "SILVER",
      "tierLabel": "Silver Member"
    },
    "error": null
  },
  "stories": {
    "status": "OK",
    "data": [
      {
        "id": 1,
        "title": "Tailored to you",
        "category": "FEATURE",
        "thumbnailUrl": "assets/stories/tailored.png",
        "gradientStart": "#4A6CF7",
        "gradientEnd": "#8B5CF6",
        "hasViewed": false
      },
      {
        "id": 2,
        "title": "Read tips in Pulse",
        "category": "EDUCATION",
        "thumbnailUrl": "assets/stories/pulse.png",
        "gradientStart": "#1E293B",
        "gradientEnd": "#334155",
        "hasViewed": true
      }
    ],
    "error": null
  },
  "spendSummary": {
    "status": "OK",
    "data": {
      "monthName": "April",
      "spentMinor": 3450000,
      "spentLkr": "34,500.00",
      "budgetLimitMinor": 5000000,
      "budgetLimitLkr": "50,000.00",
      "percentUsed": 69.0,
      "status": "ON_TRACK"
    },
    "error": null
  },
  "wallet": {
    "status": "OK",
    "data": {
      "primaryBalanceMinor": 12545000,
      "primaryBalanceLkr": "125,450.00",
      "currency": "LKR",
      "isSimulated": true,
      "defaultAccount": {
        "id": 101,
        "bankName": "Bank A",
        "maskedAccountNumber": "•••• 5678",
        "accountHolderName": "Kasun Perera"
      },
      "activeCard": {
        "id": 10,
        "maskedNumber": "•••• 0010",
        "cardLabel": "TEST CARD",
        "isFrozen": false
      },
      "totalCards": 2
    },
    "error": null
  },
  "nudge": {
    "status": "OK",
    "data": {
      "id": "nudge-01",
      "title": "CEB Electricity Bill Due in 2 Days",
      "body": "Account 0451239870 has an outstanding balance of LKR 3,450.00.",
      "actionLabel": "Pay Bill",
      "actionDeepLink": "/pay/bills/inquire?biller=CEB&acc=0451239870",
      "severity": "WARNING",
      "isDismissible": true
    },
    "error": null
  },
  "shortcuts": {
    "status": "OK",
    "data": [
      { "id": "send", "label": "Send", "icon": "send_rounded", "route": "/pay/send" },
      { "id": "scan_qr", "label": "Scan QR", "icon": "qr_code_scanner", "route": "/pay/qr" },
      { "id": "bills", "label": "Pay Bills", "icon": "receipt_long", "route": "/pay/bills" },
      { "id": "split", "label": "Split Bill", "icon": "call_split", "route": "/pay/split" },
      { "id": "goals", "label": "Save", "icon": "savings", "route": "/explore/goals" }
    ],
    "error": null
  }
}
```

### 4.2 Failure Resilience Example
If the internal rewards service experiences an unexpected failure or latency timeout, the home aggregator safely returns:
```json
{
  "rewardsPoints": {
    "status": "FAILED",
    "data": null,
    "error": "REWARDS_SERVICE_UNAVAILABLE"
  }
}
```
The mobile UI gracefully renders a subtle shimmer or placeholder for rewards, while the user's wallet, real name, stories, and payment shortcuts continue functioning seamlessly.

---

## 5. Strict Financial Rules, Data Models & Error Codes

### 5.1 The 5 Immutable Money & Simulation Rules

1. **Rule 1: Integer Minor Units + LKR Currency:**
   All monetary amounts across the database, APIs, and business logic must be represented strictly as **integer minor units** (cents / 100ths of LKR) accompanied by currency code `"LKR"`.  
   *Example:* LKR `1,500.50` is stored and transmitted as `150050`. Floats and doubles are strictly forbidden for currency math.
2. **Rule 2: Points are NOT Money:**
   Reward points are non-monetary loyalty units. They cannot be converted directly into cash, deposited into bank accounts, or transferred to third parties. They are explicitly marked `"points"` and carry zero balance-sheet liability.
3. **Rule 3: Append-Only Rewards Ledger:**
   Mutations or deletions of past reward transactions are physically impossible. The rewards ledger (`reward_ledger`) is strictly append-only. Balance adjustments must occur via explicit compensating credit/debit ledger entries.
4. **Rule 4: Universal Simulation Badging:**
   Every simulated entity, response, receipt, and UI card must carry the explicit label `SIMULATED` or `isSimulated: true`. No customer interaction may ever mislead a user into believing real fiat funds are being moved.
5. **Rule 5: Payments are Never SUCCESS Until Confirmed:**
   No payment, bill settlement, or transfer may ever return a `SUCCESS` status or issue a final receipt until the downstream `BankAdapter` or `BillerAdapter` explicitly confirms settlement. Timeouts transition strictly to `REQUIRES_RECONCILIATION`.

### 5.2 Standardized Error Codes Matrix

| Domain | Error Code | HTTP Status | Description |
|:---|:---|:---|:---|
| **System** | `FEATURE_DISABLED` | 403 Forbidden | The requested module is toggled off in this environment. |
| **System** | `AGGREGATOR_PARTIAL_FAILURE` | 200 OK | One or more home sections failed to resolve. |
| **Bills** | `BILLER_NOT_FOUND` | 404 Not Found | Specified biller code does not exist. |
| **Bills** | `BILL_ACCOUNT_INVALID` | 400 Bad Request | Account number failed biller-specific regex validation. |
| **Bills** | `BILL_ALREADY_PAID` | 409 Conflict | Outstanding balance is zero or already settled. |
| **Bills** | `BILLER_TIMEOUT` | 504 Gateway Timeout | Biller system timed out; moved to `REQUIRES_RECONCILIATION`. |
| **Scheduled** | `SCHEDULE_LIMIT_REACHED` | 400 Bad Request | Maximum active schedules (10) exceeded. |
| **Scheduled** | `INVALID_FREQUENCY` | 400 Bad Request | Unrecognized recurrence cadence. |
| **Split** | `SPLIT_SUM_MISMATCH` | 400 Bad Request | Sum of exact participant shares does not equal total bill amount. |
| **Split** | `SPLIT_ALREADY_SETTLED` | 409 Conflict | Participant has already paid their allocated share. |
| **Rewards** | `INSUFFICIENT_POINTS` | 400 Bad Request | User points balance is insufficient for requested redemption. |
| **Rewards** | `VOUCHER_EXPIRED` | 410 Gone | Selected reward voucher is out of stock or expired. |
| **Goals** | `GOAL_TARGET_INVALID` | 400 Bad Request | Goal target amount must be greater than zero. |
| **Goals** | `INSUFFICIENT_GOAL_FUNDS` | 400 Bad Request | Withdrawal amount exceeds currently saved goal balance. |
| **Support** | `THREAD_CLOSED` | 400 Bad Request | Cannot send messages to a resolved/closed support thread. |

---

## 6. Mobile Super App Migration Plan

### 6.1 Feature-First Folder Architecture
The Flutter mobile application will be organized using a feature-first structure under `mobile/lib/features/`:

```
mobile/lib/features/
├── home/          # Home dashboard, real-name greeting, stories row, spend card, wallet
├── pay/           # Consolidated Payments Hub (Send, Request, LankaQR, Bills, Split, Sched)
├── city/          # City tab: merchant directory, LankaQR places, discount vouchers
├── chat/          # Support tab: FAQ bot conversation, live support simulation
├── explore/       # Products tab: Get a card banner, 2x2 grid (Invest, Borrow, Save, Insure)
├── stories/       # Story viewer full-screen modal with auto-advancing slides
├── bills/         # Bill category list, account lookup, bill review, payment confirmation
├── scheduled/     # Scheduled payment list, creation form, pause/cancel controls
├── split/         # Split bill creation, contact selector, settlement tracking
├── analytics/     # Category pie chart, monthly budget progress bar, statement download
├── rewards/       # Rewards dashboard, points ledger, voucher redemption catalog
├── goals/         # Savings goals list, piggy bank animation, deposit/withdraw modal
└── settings/      # User profile, step-up PIN, biometrics, language selection
```

### 6.2 The 5-Tab Navigation Shell Mapping

```
┌────────────────────────────────────────────────────────────────────────┐
│                        AppBottomNav (5 Tabs)                           │
├─────────────┬─────────────┬─────────────┬──────────────┬───────────────┤
│   Tab 0     │    Tab 1    │    Tab 2    │    Tab 3     │     Tab 4     │
│   Home      │     Pay     │    City     │     Chat     │    Explore    │
│ (Dashboard) │ (Payments)  │ (Merchants) │  (Support)   │  (Products)   │
└─────────────┴─────────────┴─────────────┴──────────────┴───────────────┘
```

#### Detailed Tab Screen Mapping:
1. **Tab 0: Home (`/home`)**
   - **Header:** Authenticated user's **real registered name** (e.g. `Kasun Perera`), initial avatar badge (`[K]`), Chevron `>` (taps into `/profile`), and Rewards gift box icon (taps into `/rewards`).
   - **Search Bar:** Global search input for finding contacts, billers, and city merchants.
   - **Stories Carousel:** Horizontal scrollable stories cards matching screenshot layout. Tapping opens full-screen story viewer.
   - **2-Column Summary Row:**
     - Left: Spend summary ("All transactions / LKR 34,500.00 spent in April").
     - Right: Rewards card ("Cashback & rewards / 1,450 pts").
   - **Wallet Card:** Primary bank account / card selector, large balance display (`LKR 125,450.00 SIMULATED`), masked digits, and yellow highlighted Nudge pill banner.
   - **Shortcuts Row:** Send, Scan QR, Pay Bills, Split Bill.
2. **Tab 1: Pay (`/pay`)**
   - Consolidated payments landing screen replacing legacy isolated tabs.
   - Action grid:
     - **Send Money** (Existing `SendScreen` migrated to `/pay/send`)
     - **Scan / Pay LankaQR** (Existing `QrScreen` migrated to `/pay/qr`)
     - **Pay Utility Bills** (New `/pay/bills`)
     - **Split a Bill** (New `/pay/split`)
     - **Scheduled Transfers** (New `/pay/scheduled`)
     - **Recent Payees & Transfers stream**
3. **Tab 2: City (`/city`)**
   - Local merchant directory, categories (Food, Supermarket, Fuel, Shopping).
   - LankaQR-accepting merchant cards with simulated discounts and maps/addresses.
4. **Tab 3: Chat (`/chat`)**
   - Direct entry to Customer Support chat.
   - Displays unread message counter badge on the tab icon (matching the red `1` badge in the screenshot).
   - Pre-populated with LankaPay Assistant automated answers for common banking tasks.
5. **Tab 4: Explore (`/explore` — Products)**
   - Replicates the exact layout of the target screenshot:
     - **Top Hero Card:** "Get a card — Debit and credit cards, payment stickers and more" (links to `/cards/add` or `/accounts` Cards segment).
     - **2x2 Product Grid:**
       - **Invest** (Informational card: "Coming soon, needs a licensed partner")
       - **Borrow** (Informational card: "Coming soon, needs a licensed partner")
       - **Save** (Navigates to `/explore/goals` — Savings Goals)
       - **Insurance** (Informational card: "Coming soon, needs a licensed partner")
     - **Bottom Card:** "Family banking — Joint account, kids' card and additional cards" (Navigates to `/explore/family`).

#### Retirement & Migration of Existing Screens:
- **`MoreScreen`:** Retired from bottom tabs. Its settings, privacy toggles, biometrics, language selection, and logout options move into the **User Profile Screen (`/profile`)**, accessed by tapping the user's name/avatar on the Home header.
- **`AccountsScreen`:** Remains fully accessible by tapping the primary Wallet card on the Home screen or via "Get a card" in Explore.

---

## 7. Threat Model Additions (STRIDE Analysis)

| Threat ID | STRIDE Category | Threat Description | Architectural Mitigation |
|:---|:---|:---|:---|
| **T-23** | **Information Disclosure / Spoofing** | **Search Abuse & Phone Enumeration:** Attackers script search queries across recipient or merchant lookup endpoints to map names to phone numbers. | Enforce Bucket4j rate limits (10 lookups/min). Return truncated privacy names (`Nimal P.`) rather than full names unless previously transacted. Log `RECIPIENT_ENUMERATION_SUSPECTED` audit event on consecutive misses. |
| **T-24** | **Denial of Service** | **Scheduled-Payment Abuse & Infinite Execution:** Malicious users configure rapid recurring payments or cyclic transfer loops between accounts to exhaust background worker threads. | Limit active scheduled payments to 10 per user. Minimum recurrence interval is 24 hours. Require step-up PIN verification upon schedule creation. Hard cap batch processing threads per cycle. |
| **T-25** | **Tampering / Elevation of Privilege** | **Reward Points Fraud & Self-Dealing:** Users execute circular P2P transfers back and forth between two controlled accounts to artificially farm loyalty reward points. | Implement anti-farming heuristics: P2P transfers between mutual counterparties earn zero points. Daily reward points cap per user (maximum 250 points/day). Points ledger is strictly append-only with transactional integrity. |
| **T-26** | **Information Disclosure** | **Support Chat Data Leakage:** Users enter cleartext debit card numbers, CVVs, or PINs into support chat tickets, causing cardholder data exposure. | Implement clientside and serverside regex redaction filters for PAN, CVV, and PIN patterns in chat messages. Sanitize messages before storing in database. Mask card numbers to `•••• 1234`. |
| **T-27** | **Tampering / Repudiation** | **AI / Automated Assistant Data Hallucination:** Simulated support bot hallucinates commitments, authorizes chargebacks, or promises monetary compensation without backend validation. | The support bot is strictly non-transactional and informational. It cannot trigger refunds or alter ledger balances. All dispute requests require human administrative review through the existing admin reconciliation portal. |
| **T-28** | **Tampering / Elevation of Privilege** | **Story Content Injection & Malicious CTA Redirection (XSS):** Malicious administrative input or compromised CMS injects external URLs or script payloads into story cards. | Story slide creation is restricted to `ADMIN` role with audit logging. Story CTA links are strictly validated against an internal URI whitelist (e.g., `lankapay://*` or `/pay/*`). External HTTP/HTTPS links are strictly forbidden. |
| **T-29** | **Elevation of Privilege** | **Administrative Abuse & Unauthorized Points Minting:** Privileged operators attempt to mint reward points or override biller payment statuses arbitrarily. | Enforce dual-authorization (maker-checker) for manual points adjustments over 5,000 points. All administrative overrides emit immutable events to `audit_logs` with the administrator's authenticated actor ID. |

---

## 8. Regulatory-Honesty & Scope Boundaries

To maintain strict compliance and integrity, this project explicitly documents what is **in scope** (simulated educational features) and what is **strictly out of scope** (regulated financial services requiring licensed institutional partners).

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Scope & Compliance Matrix                       │
├──────────────────────────────────┬─────────────────────────────────────┤
│     IN SCOPE (SIMULATED)         │       STRICTLY OUT OF SCOPE         │
├──────────────────────────────────┼─────────────────────────────────────┤
│ - LankaPay ID Proxy Registry     │ - Real Investment / Securities      │
│ - Simulated Utility Billers      │ - Lending / Credit Lines / BNPL     │
│ - Virtual Cards & Card Vault     │ - Insurance Underwriting            │
│ - Gamified Loyalty Points        │ - Real Fiat Cashback Payouts        │
│ - Simulated Savings Goals        │ - Unlicensed Cross-Border Remittance│
└──────────────────────────────────┴─────────────────────────────────────┘
```

### Statutory Boundary Declarations

1. **Investments (Wealth Management, Stocks, Treasury Bills):**
   - **Out of Scope:** Operating investment products in Sri Lanka requires licensing under the **Securities and Exchange Commission of Sri Lanka (SEC) Act No. 19 of 2021**.
   - **UI Behavior:** Tapping "Invest" in the Explore grid displays an informational modal:
     > *"Investment products are currently unavailable. Live investments require integration with an SEC-licensed securities broker or unit trust manager."*
2. **Borrowing (Lending, Micro-Loans, Buy-Now-Pay-Later):**
   - **Out of Scope:** Lending activities are regulated under the **Finance Business Act No. 42 of 2011** and Central Bank of Sri Lanka directives.
   - **UI Behavior:** Tapping "Borrow" displays an informational modal:
     > *"Credit and borrowing services are currently unavailable. Micro-lending requires partnership with a CBSL-licensed commercial bank or specialized financial institution."*
3. **Insurance (Life, Health, Vehicle):**
   - **Out of Scope:** Insurance distribution requires authorization under the **Regulation of Insurance Industry Act No. 43 of 2000** by the Insurance Regulatory Commission of Sri Lanka (IRCSL).
   - **UI Behavior:** Tapping "Insurance" displays an informational modal:
     > *"Insurance policies are currently unavailable. Insurance aggregation requires partnership with an IRCSL-registered insurance company."*
4. **Real Fiat Cashback:**
   - **Out of Scope:** Directly paying fiat dividends or cashback into bank accounts without banking license implies taking unbacked deposit liabilities.
   - **UI Behavior:** The app features non-monetary **Loyalty Reward Points** redeemable strictly for partner discount vouchers; points cannot be converted into cash.

---

## 9. Phased Implementation Roadmap

To deliver the Super App smoothly without regressing existing functionality, implementation is broken down into structured, verifiable phases:

```
Phase 1 ────────────────► Phase 2 ────────────────► Phase 3 ────────────────► Phase 4
[Foundation]              [Backend Modules]         [Mobile Core]             [Polish & QA]
• Feature Flags API       • Stories & Bills         • 5-Tab Navigation Shell  • E2E Integration
• Home Aggregator API     • Scheduled & Split       • Redesigned Home Screen  • CI Secret Scan
• Resilient Section Model • Rewards & City          • Explore Products Grid   • Documentation
• User Real-Name Binding  • Support & Goals         • Pay Hub Consolidation   • Demo Script
```

### Phase 1: Foundation & Aggregator Architecture
- Implement `com.lankapay.feature` module with `GET /api/v1/features` endpoint and `@FeatureToggle` interceptor.
- Implement `com.lankapay.home` aggregator module with `GET /api/v1/home` endpoint, returning independently nullable sections and real user name resolution.
- Ensure all existing unit and integration tests remain 100% green.

### Phase 2: Core New Backend Modules
- Implement `com.lankapay.story` (Stories catalog and user read state).
- Implement `com.lankapay.bill` with `BillerAdapter` SPI and deterministic `SimulatedBillerAdapter`.
- Implement `com.lankapay.scheduled` with background payment executor and idempotency controls.
- Implement `com.lankapay.split` with equal/custom split calculator.
- Implement `com.lankapay.rewards` with immutable append-only ledger and partner vouchers.
- Implement `com.lankapay.city` with local merchants directory and discount offers.
- Implement `com.lankapay.support` with FAQ bot assistant.
- Implement `com.lankapay.goals` with simulated savings allocations.

### Phase 3: Mobile Super App Redesign
- Upgrade `AppBottomNav` and `AppShell` to the 5-tab layout (Home, Pay, City, Chat, Explore).
- Build the new `HomeScreen` matching the target layout: real user greeting, stories carousel, 2-column summary, wallet card with nudge banner, shortcuts.
- Build the `ExploreScreen` with "Get a card" banner, 2x2 product grid (Invest, Borrow, Save, Insurance), and Family Banking preview.
- Build the consolidated `PayScreen` uniting Send, LankaQR, Bills, Split, and Scheduled payments.
- Build the `CityScreen` and `ChatScreen`.
- Move Settings and Security controls from legacy `MoreScreen` to the new User Profile screen accessed from the Home avatar.

### Phase 4: Security Verification, Tests & Documentation
- Expand test coverage: unit tests for all new services, widget tests for all new screens, and golden visual regression tests.
- Verify zero regression across existing 72 backend tests and 117 mobile tests.
- Execute CI secret scan (`python scripts/scan-luhn-cards.py`) confirming zero PII/PAN leaks.
- Update `docs/api-spec.md`, `docs/data-model.md`, and `docs/demo-script.md` with super-app scenarios.
