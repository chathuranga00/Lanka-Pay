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
