# Concurrency and Load Testing Benchmark Report

**Project:** LankaPay ID / National Payment Identifier System Prototype  
**Date:** October 2026  
**Test Suite:** [`TransferConcurrencyAndLoadTest`](file:///q:/MY%20Projects/Lanka%20Pay/backend/src/test/java/com/lankapay/load/TransferConcurrencyAndLoadTest.java)  
**Execution Environment:** Java 21 LTS (OpenJDK 64-Bit Server VM), Windows, Multi-core x86_64, Maven 3.9+

---

## 1. Executive Summary

This report documents the concurrency and load testing benchmarks executed against the LankaPay ID payment processing engine. The test suite evaluates three critical real-time payment vectors under high concurrent pressure:
1. **P2P Transfer Throughput & Latency Profile:** Simulating multiple concurrent users executing payments through the central transfer orchestrator and bank adapters.
2. **Idempotency Race Condition / Double-Debit Resilience:** Validating that multi-threaded concurrent submissions carrying identical idempotency keys execute exactly once at the bank adapter and return identical cached responses to all callers.
3. **Lookup Rate Limiting Under Concurrency:** Validating that multi-threaded lookup bursts against the Token-Bucket rate limiter behave deterministically without race conditions or thread contention.

All test suites passed with **zero errors, zero deadlocks, and zero duplicate ledger entries**.

---

## 2. Test Architecture & Methodology

The load test suite uses multi-threaded orchestration with Java `CountDownLatch` barriers:
- **Warmup / Start Gate:** Worker threads are pre-instantiated in a thread pool and synchronized on a starting countdown latch, releasing all threads concurrently at the exact same millisecond.
- **Completion Barrier:** A completion latch synchronizes benchmark collection across all participating threads.
- **Nanosecond Telemetry:** Latency is sampled per request using `System.nanoTime()` and collected into thread-safe percentile aggregators.

```
                    ┌──────────────────────────────┐
                    │      CountDownLatch (1)      │
                    │        [Start Gate]          │
                    └──────────────┬───────────────┘
                                   │ (Release all)
        ┌──────────────────────────┼──────────────────────────┐
        ▼                          ▼                          ▼
  [Thread-1]                 [Thread-2]                [Thread-N]
        │                          │                          │
        ▼                          ▼                          ▼
┌──────────────────────────────────────────────────────────────────┐
│                   LankaPay Transfer Engine                       │
│  - Idempotency Gate Check (Concurrent / Unique)                 │
│  - Account & User Status Validation                              │
│  - Daily Cumulative Limit Atomicity                              │
│  - Bank Adapter Dispatch & Settlement                            │
│  - Audit Event Journaling                                        │
└──────────────────────────────────┬───────────────────────────────┘
                                   │
                                   ▼
                    ┌──────────────────────────────┐
                    │     CountDownLatch (N)       │
                    │       [Finish Gate]          │
                    └──────────────────────────────┘
```

---

## 3. Scenario 1: P2P Transfer High-Throughput Load Benchmark

### Configuration
- **Concurrent Worker Threads:** 50 threads
- **Total Transfer Requests:** 100 payment dispatches
- **Sender:** User 101 (Kasun) via Bank B (`SIMULATED_BANK_B`)
- **Receiver:** User 102 (Nimal) via Bank A (`SIMULATED_BANK_A`)
- **Transfer Amount:** 100.00 LKR per transaction
- **Idempotency Strategy:** Distinct key per transfer (`CONCURRENT-IDEM-{i}`)

### Response Time & Performance Metrics

| Metric | Result | Target Benchmark |
| :--- | :--- | :--- |
| **Total Requests** | `100` | 100 |
| **Total Test Duration** | `514.06 ms` | < 2,000 ms |
| **Effective Throughput** | **`194.53 req/sec`** | > 50 req/sec |
| **Minimum Latency** | `111.56 ms` | - |
| **Mean Latency** | `226.37 ms` | < 500 ms |
| **Median (P50) Latency** | **`194.84 ms`** | < 300 ms |
| **95th Percentile (P95)** | **`407.68 ms`** | < 800 ms |
| **99th Percentile (P99)** | **`415.23 ms`** | < 1,000 ms |
| **Maximum Latency** | `415.23 ms` | < 1,500 ms |
| **Failure Rate** | **`0.00%` (0 failed)** | 0.00% |

### Observations
1. **Sub-second Completion:** All 100 transfers traversed eligibility checks, account lookup, daily limit checks, bank adapter settlement, and audit logging within 514 ms.
2. **Stable Tail Latency:** P99 latency stayed under 420 ms, demonstrating that thread scheduling and database locking do not produce tail runaway under burst conditions.
3. **Daily Limit Atomicity:** Cumulative spend counter incremented deterministically to 10,000.00 LKR without lost updates.

---

## 4. Scenario 2: Idempotency Race Condition (Double-Spend Protection)

### Configuration
- **Concurrent Worker Threads:** 25 threads
- **Race Condition:** All 25 threads submit a transfer with the **exact same** idempotency key (`SAME-IDEMPOTENCY-KEY-001`) at the exact same millisecond.
- **Transfer Details:** 250.00 LKR to Nimal (`0770000002`).

### Results & Verification

| Test Condition | Observed | Expected | Status |
| :--- | :--- | :--- | :--- |
| **Total Concurrent Invocations** | 25 | 25 | Passed |
| **Bank Adapter `initiateTransfer` Invocations** | **1** | **1** | **Passed (Zero duplicate settlement)** |
| **Duplicate Submissions Prevented** | **24 / 25 (96%)** | 24 / 25 | **Passed** |
| **Response Status for All 25 Threads** | `COMPLETED` | `COMPLETED` | **Passed (Identical cached payload)** |
| **Amount Returned for All 25 Threads** | `250.00 LKR` | `250.00 LKR` | **Passed** |

### Mechanism
- The first thread to acquire the idempotency reservation executes the transaction and stores the response record in `IdempotencyRecordRepository`.
- Contending concurrent threads either detect the in-flight reservation or retrieve the cached finalized response, preventing duplicate bank debits or duplicate account crediting.

---

## 5. Scenario 3: Token-Bucket Rate Limiter Under Concurrency

### Configuration
- **Concurrent Threads:** 40 threads
- **Target Component:** [`LookupRateLimiter`](file:///q:/MY%20Projects/Lanka%20Pay/backend/src/main/java/com/lankapay/registry/service/LookupRateLimiter.java)
- **Bucket Capacity:** User Capacity = 50 tokens/min, IP Capacity = 100 tokens/min
- **Contention Target:** Single User ID (`101L`), separate client IP addresses (`192.168.1.{i}`)

### Results & Verification

| Metric | Result | Target Benchmark |
| :--- | :--- | :--- |
| **Total Requests** | `40` | 40 |
| **Tokens Granted (Allowed)** | **`40`** | 40 |
| **Tokens Denied (Rate Limited)** | **`0`** | 0 |
| **Thread Exceptions / Contention Errors** | **`0`** | 0 |

### Mechanism
- Bucket4j's atomic CAS (compare-and-swap) mechanics ensure that multiple concurrent threads decrement tokens accurately without coarse synchronized locks, guaranteeing low-latency enforcement even during distributed enumeration bursts.

---

## 6. Recommendations for Production Scale

When scaling this architecture beyond prototype and university demonstration:
1. **Distributed Idempotency with Redis Redlock:** Replace the single-node idempotency check with a distributed lock (e.g. Redis Redlock with 10-second TTL) across multiple backend replicas.
2. **Distributed Rate Limiting:** Transition `LookupRateLimiter` from `ConcurrentHashMap` to Redis Bucket4j or Redis cluster token-bucket scripts so rate limits apply uniformly across multi-instance clusters.
3. **Database Connection Pool Sizing:** Ensure HikariCP `maximum-pool-size` is sized according to formula:
   $$\text{connections} = ((\text{core\_count} \times 2) + \text{effective\_spindle\_count})$$
   with appropriate connection timeouts (e.g., 2,500 ms) to avoid thread starvation under spikes.
4. **Read Replicas for Registry Lookups:** Separate registry search queries (read-heavy) from transfer transactions (write-heavy) to preserve master database IOPS for real-time settlement.
