# LankaPay ID — Known System Limitations & Prototype Boundaries

**Project:** LankaPay ID National Payment Identifier  
**Classification:** Academic Capstone Prototype  
**Scope:** Architecture & Implementation Assessment

This document explicitly delineates the operational boundaries, technical trade-offs, and known limitations of the LankaPay ID platform in its current prototype implementation.

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
