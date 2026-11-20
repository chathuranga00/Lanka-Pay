# LankaPay ID — Known System Limitations & Prototype Boundaries

**Project:** LankaPay ID National Payment Identifier & Simulated Banking Super App  
**Classification:** Academic Capstone Prototype  
**Scope:** Architecture & Implementation Assessment

> ⚠️ **STATUTORY & REGULATORY DISCLAIMER**  
> **LankaPay ID is an educational and technological simulation.** It is **not** an official product of LankaClear (Pvt) Ltd, the Central Bank of Sri Lanka (CBSL), or any commercial bank or financial institution.  
> **All banks, clearing switches, utility billers, card vaults, loyalty programs, and financial accounts are 100% SIMULATED.**  
> **No real money is transferred, held, debited, or credited.** Live financial operations in Sri Lanka mandate formal licensing under the Payment and Settlement Systems Act No. 28 of 2005, CBSL Payment Service Provider (PSP) authorization, and network membership in LankaClear (CEFTS/JustPay/LankaQR).

This document explicitly delineates the operational boundaries, technical trade-offs, and known limitations of the LankaPay ID platform across both its foundational proxy payment engine and its simulated Super-App modules.

---

## 1. Banking & Settlement Simulation Layer

| Dimension | Prototype Implementation | Production Reality | Impact & Mitigation |
| :--- | :--- | :--- | :--- |
| **Monetary Value** | **100% Simulated.** No real funds, bank balances, or central bank reserves are held or transferred. | Real-time debit and credit of actual bank deposit accounts via CBSL RTGS / CEFTS. | Prevents financial loss in test environments; system clearly tagged with `[SIMULATED]` in all UI screens and API logs. |
| **Bank Gateways** | In-memory simulated adapters (`SimulatedBankAdapterA/B/C`). | Host-to-Host (H2H) ISO 20022 clearing interfaces over dedicated leased lines. | Deterministic test responses (e.g. timeout on 30,000 LKR; decline on low balances); real bank APIs require commercial agreements. |
| **Account Validation** | Synthetic account database with pre-seeded mock balances. | Core Banking System (CBS) integration via Finacle, Flexcube, or Temenos T24. | Account linking uses mock verification without requiring direct open banking mandates. |

---

## 2. Identity Verification & e-KYC

| Dimension | Prototype Implementation | Production Reality | Impact & Mitigation |
| :--- | :--- | :--- | :--- |
| **NIC Verification** | Regex validation for Old format (9 digits + `V`/`X`) and New format (12 digits). | Real-time government API lookup against Department for Registration of Persons (DRP). | Prototype validates format and uniqueness but cannot verify biometric identity or forged credentials. |
| **Liveness & Biometrics** | Client-side PIN authentication and local mock biometric prompt. | e-KYC liveness detection (face match against DRP database) and facial recognition. | Production deployment requires integration with a licensed e-KYC provider. |
| **SIM Card Binding** | Phone number claimed during registration; OTP verified. | Telecom network carrier lookup (IMSI / IMEI binding) to detect SIM swap attacks. | Real banking apps bind cryptographic device keys to the physical SIM card to prevent hijacking. |

---

## 3. SMS & Telecommunication Integration

| Dimension | Prototype Implementation | Production Reality | Impact & Mitigation |
| :--- | :--- | :--- | :--- |
| **OTP Delivery** | In-memory token generator (`SmsService`); OTP logged to console; fixed fallback `123456` in test mode. | Commercial SMPP gateway connection (e.g., Dialog IdeaBiz, Mobitel mCash, or Sinch). | Eliminates telecom SMS fees during development and evaluation; unacceptable for production authentication. |
| **Delivery Latency** | Instantaneous (< 5 ms in memory). | Telecom network propagation (typically 2 to 10 seconds). | Real app must handle SMS network delays, re-send countdown timers, and carrier outages. |

---

## 4. Scalability, Caching & Distributed State

| Dimension | Prototype Implementation | Production Reality | Impact & Mitigation |
| :--- | :--- | :--- | :--- |
| **Rate Limiter Storage** | In-memory `ConcurrentHashMap` via Bucket4j in a single JVM instance. | Distributed Redis cluster using Redis Token-Bucket Lua scripts. | Restarting the backend clears active rate-limit counters; limits are node-local rather than global across replicas. |
| **Idempotency Locking** | Relational database constraint and row-level locking via MySQL / JPA. | Distributed memory lock (e.g., Redis Redlock) with millisecond expiration. | Highly effective for single or dual-node deployments; at massive scale (> 5,000 TPS), distributed locks prevent database lock contention. |
| **Caching Layer** | Direct database reads with Hibernate first-level cache. | Multi-tier caching with Redis / Hazelcast for bank routing codes and user discoverability flags. | Prototype queries database on each registry lookup; production requires sub-millisecond edge caching. |

---

## 5. Security & Cryptographic Key Management

| Dimension | Prototype Implementation | Production Reality | Impact & Mitigation |
| :--- | :--- | :--- | :--- |
| **PIN Storage & Verification** | Software BCrypt hashing (`BCryptPasswordEncoder`, cost factor 12) in Java runtime. | Hardware Security Module (HSM) zone encryption; PINs never decrypted in memory. | Secure against offline database dumps; production compliance (PCI-DSS) strictly mandates hardware-based HSM PIN blocks. |
| **Data-at-Rest Encryption** | Application-level AES-256 GCM using key injected from environment variable. | Dedicated Key Management Service (AWS KMS, Google Cloud KMS, or HashiCorp Vault) with automated key rotation. | Simple to configure locally; production requires dual-custody key rotation and envelope encryption. |

---

## 6. Offline & Network Partition Handling

| Dimension | Prototype Implementation | Production Reality | Impact & Mitigation |
| :--- | :--- | :--- | :--- |
| **Offline Functionality** | Mobile app requires active internet connectivity (Wi-Fi or cellular data) to communicate with API. | Offline QR display caching; queueing payment intents locally. | In low-connectivity rural environments, real apps maintain signed offline dynamic QR tokens with cryptographic nonces. |
| **Network Partitions** | Transfers experiencing connection drops move to `REQUIRES_RECONCILIATION`. | 2-Phase Commit (2PC) or Saga distributed transaction orchestrator with automated clearing compensation. | The prototype relies on administrative manual reconciliation for simulated timeout edge cases. |

---

## 7. Dispute Management & Chargebacks

| Dimension | Prototype Implementation | Production Reality | Impact & Mitigation |
| :--- | :--- | :--- | :--- |
| **Reversals** | Administrator or system triggers reversal directly via `AdminController` or `ReversalService`. | Standard LankaPay Dispute Resolution Rules (DRR) with 7-day investigation cycle and automated clearing journals. | Prototype demonstrates functional reversal mechanics, but does not model legal arbitration workflows. |

---

## 8. Super-App Module Operational Boundaries & Trade-Offs

| Module | Prototype Implementation | Production Reality | Statutory / Regulatory Boundary |
| :--- | :--- | :--- | :--- |
| **Utility Bills (`com.lankapay.bill`)** | In-memory `SimulatedBillerAdapter` with deterministic account rules (`0001` payable, `0002` overdue, `0009` offline) across simulated billers (CEB, LECO, NWSDB, Dialog, SLT). | Real-time bill presentment and clearing via LankaPay Electronic Bill Presentment & Payment (EBPP) or direct biller host-to-host APIs. | The prototype demonstrates minor-unit billing arithmetic, idempotency, and step-up authorization without live utility company clearing agreements. |
| **Scheduled Payments (`com.lankapay.scheduled`)** | JVM-local `@Scheduled` task executor triggering `TransferProcessor` with per-cycle idempotency keys (`SCHED-{id}-{date}`). | Distributed transaction orchestrator (e.g. Temporal, Quartz Cluster, or Celery) integrated with bank Standing Orders (SO) or CBSL Direct Debit mandates. | Node restart or failure does not persist in-flight execution states across clusters; production mandates durable workflow orchestration and formal customer electronic mandate agreements. |
| **Split Bill (`com.lankapay.split`)** | Social split calculation engine (equal/exact) and proxy identifier assignment. Notifications dispatched to participants. | Direct debit authorization or instant UPI/LankaPay collect requests requiring counterparty bank authorization. | Prototype records social debts and tracks settlement state without legal claim or involuntary funds deduction from counterparty bank accounts. |
| **Loyalty Rewards (`com.lankapay.rewards`)** | Non-fiat gamified loyalty points ledger (`pts`) awarded on successful transactions and reversed on payment reversal. Points redeemed for simulated merchant discount vouchers. | Regulated financial cashback or interest payout directly to deposit accounts. | **Strictly non-monetary.** Points have zero cash surrender value and cannot be withdrawn or converted to fiat currency, avoiding unbacked deposit liability regulations. |
| **City Merchant Directory (`com.lankapay.city`)** | In-memory repository of simulated merchant locations, categories, and promotional discounts. | Merchant acquiring network with POS terminal provisioning, LankaQR merchant acquirer registration, and merchant settlement accounts. | Prototype evaluates search, category filtering, and promotional discount display without live merchant contractual onboarding. |
| **Support Chat (`com.lankapay.support`)** | In-memory rule-based automated FAQ assistant (`LankaPay Assistant`) and simulated live agent messaging session. Regex redacts PAN, CVV, and PIN inputs. | AI customer support platform integrated with bank CRM (e.g., Salesforce, Zendesk) and authenticated core banking dispute management. | **Strictly non-transactional.** The bot is purely informational and cannot disburse funds, alter ledgers, or promise monetary adjustments autonomously. |
| **Interactive Stories (`com.lankapay.story`)** | Static and simulated dynamic story cards with order indices and read tracking. Slide deep links restricted to internal app route scheme (`lankapay://*` or `/pay/*`). | Enterprise Content Management System (CMS) with targeted marketing segmentation, analytics, and CDN caching. | Prevents open redirection / XSS vulnerabilities; prototype validates content schema and local user read states without external tracking pixels. |
| **Financial Products Grid (`Explore`)** | "Save" links to simulated Savings Goals (`com.lankapay.goals`). "Invest", "Borrow", and "Insurance" display informational "Coming Soon" dialogs explaining statutory licensing requirements. | Licensed investment brokerage (SEC Act No. 19 of 2021), licensed micro-finance lending (Finance Business Act No. 42 of 2011), and authorized insurance underwriting (IRCSL Act No. 43 of 2000). | **Strictly out of scope.** Demonstrates regulatory honesty by declining to simulate unlicensed securities, credit issuance, or insurance underwriting. |
| **Card Vault & Tokenization (`SimulatedCardVault`)** | In-memory software tokenization returning opaque tokens (`SIM-VAULT-TOKEN-*`) and masked PANs (`•••• 1234`). | Certified PCI-DSS Level 1 tokenization gateway with Visa Token Service (VTS) / Mastercard Digital Enablement Service (MDES) network tokens. | **Never enter real card data.** Educational testing only; see [Real Integration Roadmap](file:///q:/MY%20Projects/Lanka%20Pay/docs/real-integration-roadmap.md#7-replacing-simulated-card-vault-with-certified-pci-dss-gateway--issuer-programme) for production migration requirements. |
