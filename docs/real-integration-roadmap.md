# LankaPay ID — Real Integration & Regulatory Roadmap (Phase 7)

> ⚠️ **IMPORTANT ARCHITECTURAL DISCLAIMER**  
> This prototype currently operates **exclusively with simulated bank adapters (`SIMULATED_BANK_A`, `SIMULATED_BANK_B`, `SIMULATED_BANK_C`)** and in-memory test stubs.  
> **This repository does NOT claim that direct private APIs currently exist for all Sri Lankan commercial banks, nor does it contain unauthorized or proprietary banking endpoints.**  
> Moving from this technological prototype to a live, production-grade payment network requires formal regulatory licensing, commercial banking sponsorship, and clearing house certification as documented in this roadmap.

---

## 1. Regulatory & Statutory Framework (Central Bank of Sri Lanka)

Operating a national or retail payment proxy registry in Sri Lanka requires formal authorization and licensing under CBSL regulatory oversight:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   Central Bank of Sri Lanka (CBSL)                     │
│                Payments and Settlements Department (PSD)                │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
         ┌─────────────────────────┼─────────────────────────┐
         ▼                         ▼                         ▼
┌──────────────────┐      ┌──────────────────┐      ┌──────────────────┐
│  Payment &       │      │  Customer Due    │      │  Personal Data   │
│  Settlement Act  │      │  Diligence / AML │      │  Protection Act  │
│  No. 28 of 2005  │      │  (FTRA No. 06)   │      │  No. 09 of 2022  │
└──────────────────┘      └──────────────────┘      └──────────────────┘
```

### Statutory Requirements Checklist
- [ ] **Operator License:** Application under the **Payment and Settlement Systems Act No. 28 of 2005** as a Payment Service Provider (PSP) or Operator of a Payment System.
- [ ] **Mobile Payment Guidelines:** Compliance with CBSL Guidelines on Mobile-Based Payment Systems (Direction No. 02 of 2011 and subsequent revisions).
- [ ] **Customer Due Diligence (CDD) & AML/CFT:** Adherence to the **Financial Transactions Reporting Act (FTRA) No. 06 of 2006**, requiring real-time screening against UN Sanctions lists, politically exposed persons (PEP) monitoring, and transaction threshold reporting to the Financial Intelligence Unit (FIU).
- [ ] **Data Residency & Privacy (PDPA No. 09 of 2022):** Strict enforcement of local data residency—all financial ledger records, transaction history, and consumer cryptographic material must be stored in physical data centers within the sovereign territory of Sri Lanka.
- [ ] **Electronic Transactions Act No. 19 of 2006:** Legal recognition of digital PIN authorization, digital receipts, and audit trail admissibility in Sri Lankan courts.

---

## 2. Partner Outreach & Commercial Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                   LankaPay ID Proxy Engine                       │
└─────────────────┬──────────────────────────────┬─────────────────┘
                  │                              │
                  ▼                              ▼
     ┌────────────────────────┐      ┌────────────────────────┐
     │  Sponsor Clearing Bank │      │  LankaPay (Pvt) Ltd    │
     │  - Escrow Settlement   │      │  - CEFTS Operator      │
     │  - CBSL Direct Member  │      │  - JustPay Network     │
     │  - Liquidity Guarantee │      │  - National LankaQR    │
     └────────────┬───────────┘      └────────────┬───────────┘
                  │                               │
                  └───────────────┬───────────────┘
                                  ▼
           ┌──────────────────────────────────────────────┐
           │      Licensed Commercial Banks (LCBs)        │
           │      & Licensed Specialized Banks (LSBs)     │
           │  (Bank of Ceylon, Commercial Bank, HNB,      │
           │   Sampath Bank, Peoples Bank, etc.)          │
           └──────────────────────────────────────────────┘
```

### Institutional Partnerships
1. **LankaPay (Pvt) Ltd Partnership:**
   - Formal onboarding onto the **Common Electronic Fund Transfer Switch (CEFTS)** for instant inter-bank credit transfers.
   - Integration into the **JustPay** payment mechanism for low-value retail debit transactions (up to 50,000 LKR per transaction).
   - Accredited acquirer / switch certification for **LankaQR** merchant and P2P routing.
2. **Sponsor Bank Agreement:**
   - Partnership with a tier-1 Licensed Commercial Bank (LCB) to act as the Settlement Sponsor, holding the central settlement escrow account and guaranteeing inter-bank net settlement obligations.
3. **Bilateral Commercial Bank Agreements:**
   - Standardized Service Level Agreements (SLAs) with participating banks governing uptime (99.95%+), dispute turnaround times (< 24 hours), and mutual indemnity clauses.

---

## 3. Technical Certification & Protocol Migration

The current REST/JSON simulation must be replaced with industry-standard clearing protocols:

| Functional Area | Current Prototype | Production Standard | Migration Requirements |
| :--- | :--- | :--- | :--- |
| **Inter-bank Messaging** | REST / Mock Adapters | **ISO 20022 XML** | Implementation of `pacs.008` (Financial Institutional Customer Credit Transfer), `pacs.002` (Payment Status Report), and `camt.053` (Cash Management Statement). |
| **Retail Debit** | Internal Transfer Engine | **JustPay API Protocol** | Strict validation of JustPay digital certificate signing, bank tokenization, and sponsor bank validation. |
| **QR Code Processing** | Static/Dynamic JSON Token | **EMVCo / LankaQR Spec** | Compliance with EMVCo Merchant-Presented and Consumer-Presented QR specifications with Tag 00 to Tag 63 CRC16 checksums. |
| **Network Transport** | Public Internet HTTPS | **Dedicated Leased Lines / VPN** | Dual redundant IPsec VPN tunnels or LankaPay Network Leased Lines with Mutual TLS (mTLS) using X.509 RSA-4096 / ECC certificates. |

---

## 4. Security Assessments & Independent Audits

Before live traffic is permitted, the platform must undergo rigorous external certification:

1. **PCI-DSS Level 1 Certification:**
   - Attestation of Compliance (AoC) by a Qualified Security Assessor (QSA) covering payment data transmission, key rotation, and network segmentation.
2. **CBSL-Accredited VAPT (Penetration Testing):**
   - Independent grey-box and black-box penetration testing and code security audits by an accredited cybersecurity firm (e.g., CREST or Sri Lanka CERT accredited).
3. **Hardware Security Module (HSM) Integration:**
   - Migration from software BCrypt PIN storage to FIPS 140-2 Level 3 Hardware Security Modules (e.g., Thales payShield 10K or Entrust nShield).
   - Implementation of ANSI X9.8 / ISO 9564 PIN blocks (PIN Block Format 0 or 4) ensuring consumer PINs are never decrypted in application memory.
4. **Key Management Ceremonies:**
   - Documented cryptographic key generation ceremonies under dual control and split knowledge for zone master keys (ZMK) and zone PIN keys (ZPK).

---

## 5. Operations, Disaster Recovery & High Availability

| Resilience Domain | Minimum CBSL Requirement | Architectural Solution |
| :--- | :--- | :--- |
| **Data Residency** | 100% On-shore within Sri Lanka | Active-Active infrastructure across primary Colombo data center and secondary disaster recovery (DR) site outside Colombo seismic zone. |
| **Recovery Time Objective (RTO)** | < 15 Minutes | Automated failover orchestration using health-checking global load balancers and synchronized databases. |
| **Recovery Point Objective (RPO)** | **0 Minutes (Zero data loss)** | Synchronous replication on all core payment ledgers; semi-synchronous replication on read mirrors. |
| **Availability (SLA)** | 99.95% Annual Uptime | Zero single points of failure across web, application, and database clusters. |
| **Dispute Settlement** | CBSL PSD Directive Compliance | Daily multi-lateral net settlement batches with automated end-of-day reconciliation journals and 7-day chargeback resolution window. |

---

## 6. Phased Implementation Timeline

```
Q1 ──────────────────────► Q2 ──────────────────────► Q3 ──────────────────────► Q4
[Phase 7.1]                [Phase 7.2]                [Phase 7.3]                [Phase 7.4]
• CBSL Consultation        • LankaPay CEFTS Sandbox   • ISO 20022 Integration    • Live Controlled Pilot
• Sponsor Bank Agreement   • HSM Provisioning         • VAPT & PCI-DSS Audit     • Commercial Launch
• Legal & PDPA Review      • mTLS Network Setup       • End-to-End Certification • Public Rollout
```

---

## 7. Replacing Simulated Card Vault with Certified PCI-DSS Gateway / Issuer Programme

> ⚠️ **VAULT SIMULATION NOTICE**  
> The current card tokenization engine (`SimulatedCardVault`) and virtual cards service are purely educational reference implementations that simulate token issuance, card storage, and OTP challenges in memory. **No real payment gateway or bank card management APIs exist in this repository.** Production deployment mandates replacing all simulated card components with an accredited PCI-DSS Level 1 payment gateway or licensed issuer processor.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       Certified Gateway / Issuer Architecture                │
└─────────────────────────────────────────────────────────────────────────────┘
                                  │
  1. Hosted Tokenization SDK      │  2. Direct Token Call
  ┌─────────────────────────┐     │  ┌────────────────────────────────────┐
  │  Mobile App (Flutter)   │─────┼─►│ Certified PCI-DSS Level 1 Gateway  │
  │  - Hosted Fields / SDK  │     │  │ - PAN & CVV Tokenization Engine    │
  └───────────┬─────────────┘     │  └──────────────────┬─────────────────┘
              │ 3. Opaque Token   │                     │ 4. Network Token
              ▼                   │                     ▼
  ┌─────────────────────────┐     │  ┌────────────────────────────────────┐
  │  LankaPay Backend Core  │─────┼─►│ Card Schemes (Visa / Mastercard)   │
  │  - Zero PAN / Zero CVV  │     │  │ - Network Token Service (VTS/MDES) │
  │  - PCI SAQ-A / A-EP     │     │  │ - 3-D Secure 2.x ACS / Directory   │
  └─────────────────────────┘     │  └────────────────────────────────────┘
```

The migration journey from simulation to production comprises seven critical workstreams:

### 7.1 Legal Entity & Regulatory Licensing
1. **Corporate Structure & Capitalization:** Establish a recognized domestic corporate entity meeting CBSL minimum capital requirements for Payment Service Providers (PSPs) and electronic fund operators.
2. **CBSL Payment Card Guidelines:** Secure approval under CBSL Mobile Payment and Electronic Card guidelines to act as a digital wallet card-on-file aggregator or virtual card co-brand programme manager.
3. **Card Scheme Membership / Sponsorship:** Secure indirect sponsorship via a licensed domestic sponsor bank or direct associate license with international/national payment card networks (e.g., Visa, Mastercard, LankaPay National Card Scheme).

### 7.2 PCI-DSS Compliance Review & CDE Scoping
1. **Cardholder Data Environment (CDE) Elimination:**
   - Architecture must ensure internal application servers and databases **never handle, transmit, process, or store** Primary Account Numbers (PANs), CVVs, or unmasked expiration dates.
   - Attain eligibility for **PCI-DSS Self-Assessment Questionnaire (SAQ) A** (or **SAQ A-EP** if customized client wrappers are utilized), minimizing audit burden.
2. **Qualified Security Assessor (QSA) Audit:** Retain a certified third-party QSA to evaluate architecture, key management ceremonies, TLS termination, and log aggregation.
3. **Approved Scanning Vendor (ASV) Scans:** Institute mandatory quarterly external vulnerability scans of all internet-facing domains by an accredited ASV.

### 7.3 Gateway & Issuer Processor Onboarding
1. **RFP & Partner Selection:** Contract with an accredited PCI-DSS Level 1 payment gateway or issuer processing platform.
2. **Contractual SLAs & Indemnification:** Establish legal service level agreements ensuring 99.99% gateway availability, sub-500ms authorization latency, and PCI incident indemnification.
3. **Mutual TLS & Secure Webhooks:** Provision mutual TLS (mTLS) with client certificates for all server-to-server gateway communication. Webhook callbacks for charge notifications, refunds, and token updates must be authenticated via HMAC-SHA256 asymmetric signature verification.

### 7.4 Hosted-Field Tokenization (Client-Side)
1. **Replacement of Direct Vault Endpoint:**
   - Deprecate `/api/v1/sim-vault/tokenize` on backend servers.
   - Integrate the certified gateway's mobile SDK or secure hosted webview fields directly into the Flutter mobile application.
2. **Ephemeral Token Generation:**
   - The user inputs their PAN, expiry, and CVV exclusively inside sandboxed hosted fields rendered by the gateway SDK.
   - The gateway SDK sends the sensitive card data directly to the gateway's certified CDE and returns an opaque, single-use token or permanent customer card identifier to the mobile app.
   - The mobile app sends only the non-sensitive token reference and metadata (masked digits, card scheme) to the LankaPay backend.

### 7.5 Network Tokens & Token Lifecycle Management
1. **Network Tokenization Provisioning:**
   - Implement Network Tokenization via Visa Token Service (VTS) or Mastercard Digital Enablement Service (MDES) through the onboarding gateway.
   - Network tokens replace regular gateway tokens, tying payment credentials to the specific merchant domain, mobile device, and cryptographic cryptogram.
2. **Token Lifecycle & Automatic Account Updater:**
   - Register webhook listeners for card reissue, expiry renewal, and bank account status changes.
   - Network tokens remain valid even when the physical card is reissued due to expiry or loss, preventing payment disruptions without re-entering card numbers.

### 7.6 3-D Secure 2.x (EMV 3DS) Implementation
1. **EMV 3DS Client Integration:** Embed the gateway's certified 3DS Mobile SDK to gather device telemetry (device fingerprints, screen resolution, OS version) required by the issuing bank's fraud engine.
2. **Frictionless vs. Challenge Flow:**
   - Transmit required risk indicators to the 3DS Server (3DSS).
   - If low risk, the issuing bank grants frictionless authentication with immediate liability shift.
   - If challenge is required, the SDK displays the issuer's native Access Control Server (ACS) challenge screen (e.g., bank SMS OTP or banking app biometric prompt), completely decoupled from the merchant application backend.
3. **Liability Shift Verification:** Validate electronic commerce indicator (ECI) values and Cardholder Authentication Verification Value (CAVV) before dispatching the authorization request.

### 7.7 Chargebacks, Disputes & Fraud Handling
1. **Dispute Ingestion & Representment Workflow:**
   - Build automated event listeners for First Chargebacks, Retrieval Requests, and Pre-Arbitration notices delivered via gateway webhooks.
   - Implement an operator back-office workflow allowing compliance staff to upload digital proof of delivery, IP logs, device identifiers, and EMV 3DS CAVV authentication receipts within scheme dispute turnaround windows (typically 7 to 14 calendar days).
2. **Real-time Velocity & Fraud Scoring:**
   - Integrate automated risk rules: maximum transaction amounts per card per rolling 24 hours, velocity triggers for consecutive failed attempts, card-to-account diversity limits, and anomalous device switches.
   - Automatically freeze virtual cards or decline authorizations when fraud probability exceeds configured risk thresholds.

