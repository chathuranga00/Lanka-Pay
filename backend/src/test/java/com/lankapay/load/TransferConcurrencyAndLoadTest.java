package com.lankapay.load;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.adapter.BankAdapter;
import com.lankapay.bank.adapter.BankAdapterRegistry;
import com.lankapay.bank.adapter.simulated.*;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.LinkedAccount;
import com.lankapay.bank.domain.LinkedAccountStore;
import com.lankapay.common.exception.RateLimitException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.registry.service.LookupRateLimiter;
import com.lankapay.transfer.domain.TransactionRecord;
import com.lankapay.transfer.domain.TransactionStatus;
import com.lankapay.transfer.domain.TransactionStore;
import com.lankapay.transfer.domain.TransferInitiatedVia;
import com.lankapay.transfer.service.DailyLimitService;
import com.lankapay.transfer.service.IdempotencyService;
import com.lankapay.transfer.service.TransferProcessor;
import com.lankapay.transfer.service.TransferService;
import com.lankapay.transfer.web.dto.InitiateTransferRequest;
import com.lankapay.transfer.web.dto.TransferResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Concurrency and Load Tests with microsecond-level latency measurement.
 *
 * Scenarios:
 * 1. High Concurrency Transfer Processing:
 *    50 concurrent threads executing 100 transfer requests across simulated banks.
 * 2. Idempotency Race Condition Protection:
 *    25 concurrent threads submitting the exact same transfer request with the identical
 *    Idempotency-Key simultaneously via CountDownLatch barrier.
 * 3. Concurrent Registry Lookup Rate-Limiting:
 *    40 concurrent threads performing lookups to evaluate token-bucket thread safety.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TransferConcurrencyAndLoadTest {

    private static final Logger log = LoggerFactory.getLogger(TransferConcurrencyAndLoadTest.class);

    @Mock TransactionStore transactionStore;
    @Mock LinkedAccountStore linkedAccountStore;
    @Mock UserStore userStore;
    @Mock AuditService auditService;

    private PasswordEncoder passwordEncoder;
    private ObjectMapper objectMapper;
    private SimulatedAccountStore simulatedAccountStore;
    private BankAdapterRegistry bankAdapterRegistry;
    private TransferProcessor transferProcessor;
    private DailyLimitService dailyLimitService;
    private IdempotencyService idempotencyService;
    private TransferService transferService;

    private User senderUser;
    private User receiverUser;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        simulatedAccountStore = new SimulatedAccountStore();
        List<BankAdapter> adapters = List.of(
                new SimulatedBankAdapterA(simulatedAccountStore),
                new SimulatedBankAdapterB(simulatedAccountStore),
                new SimulatedBankAdapterC(simulatedAccountStore)
        );
        bankAdapterRegistry = new BankAdapterRegistry(adapters);

        dailyLimitService = new DailyLimitService(transactionStore);
        transferProcessor = new TransferProcessor(
                transactionStore, bankAdapterRegistry, dailyLimitService, auditService, null);
        idempotencyService = new IdempotencyService(transactionStore, objectMapper);

        transferService = new TransferService(
                transactionStore, userStore, linkedAccountStore,
                transferProcessor, dailyLimitService, idempotencyService, passwordEncoder, null);

        // Sender
        senderUser = new User("+94771000001", "Kasun Perera [TEST]", "199010000001", passwordEncoder.encode("123456"));
        senderUser.setStatus(UserStatus.ACTIVE);
        senderUser.setRole(UserRole.CUSTOMER);
        ReflectionTestUtils.setField(senderUser, "id", 101L);

        LinkedAccount senderLinked = new LinkedAccount(
                202L, 101L, "SIMULATED_BANK_B", "hashB", "****1001", "Kasun Perera [TEST]",
                null, true, true, "encRefB", BankAccountStatus.ACTIVE, true, Instant.now(), Instant.now());

        // Receiver
        receiverUser = new User("+94771000002", "Nimal Silva [TEST]", "199010000002", passwordEncoder.encode("123456"));
        receiverUser.setStatus(UserStatus.ACTIVE);
        receiverUser.setRole(UserRole.CUSTOMER);
        ReflectionTestUtils.setField(receiverUser, "id", 102L);

        LinkedAccount receiverLinked = new LinkedAccount(
                204L, 102L, "SIMULATED_BANK_A", "hashA", "****1002", "Nimal Silva [TEST]",
                null, true, true, "encRefA", BankAccountStatus.ACTIVE, true, Instant.now(), Instant.now());

        // Stubs
        when(userStore.findUserById(101L)).thenReturn(Optional.of(UserAccount.fromUser(senderUser)));
        when(userStore.findUserById(102L)).thenReturn(Optional.of(UserAccount.fromUser(receiverUser)));
        when(linkedAccountStore.findActiveDefaultAccount(101L)).thenReturn(Optional.of(senderLinked));
        when(linkedAccountStore.findActiveDefaultAccount(102L)).thenReturn(Optional.of(receiverLinked));
        when(transactionStore.findDailyLimit(any(), any())).thenReturn(Optional.empty());

        AtomicLong txIdCounter = new AtomicLong(1000L);
        when(transactionStore.createTransferWithIdempotency(any(TransactionRecord.class), anyString(), anyString()))
                .thenAnswer(inv -> {
                    TransactionRecord r = inv.getArgument(0);
                    Long id = txIdCounter.incrementAndGet();
                    return new TransactionRecord(
                            id, r.reference(), r.idempotencyKey(),
                            r.senderId(), r.senderPhone(), r.senderFullName(),
                            r.receiverId(), r.receiverPhone(), r.receiverFullName(),
                            r.senderBankCode(), r.receiverBankCode(),
                            r.amount(), r.currency(), TransactionStatus.PENDING,
                            null, null, null, r.note(), true, r.initiatedVia(),
                            r.qrCodeId(), 1L, Instant.now(), Instant.now()
                    );
                });

        when(transactionStore.transitionTransferStatus(anyLong(), any(), any(), any(), any(), any(), any(), anyLong()))
                .thenAnswer(inv -> {
                    Long id = inv.getArgument(0);
                    TransactionStatus status = inv.getArgument(1);
                    String bankTxRef = inv.getArgument(4);
                    String provRef = inv.getArgument(5);
                    String failReason = inv.getArgument(6);
                    Long ver = inv.getArgument(7);
                    return new TransactionRecord(
                            id, "ref-" + id, "key-" + id,
                            101L, "+94771000001", "Kasun Perera [TEST]",
                            102L, "+94771000002", "Nimal Silva [TEST]",
                            "SIMULATED_BANK_B", "SIMULATED_BANK_A",
                            new BigDecimal("100.00"), "LKR", status,
                            failReason, bankTxRef != null ? bankTxRef : "SIM-TX-" + id, provRef, "note", true, TransferInitiatedVia.APP,
                            null, (ver != null ? ver : 1L) + 1L, Instant.now(), Instant.now()
                    );
                });
    }

    @Test
    @DisplayName("Load Benchmark: 50 concurrent threads processing 100 transfers")
    void testConcurrentTransferThroughputAndLatency() throws Exception {
        int totalRequests = 100;
        int concurrency = 50;

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalRequests);

        List<Long> latenciesNanos = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        long overallStartNanos = System.nanoTime();

        for (int i = 0; i < totalRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize all threads to fire simultaneously
                    long reqStart = System.nanoTime();

                    String key = "CONCURRENT-IDEM-" + index;
                    InitiateTransferRequest req = new InitiateTransferRequest(
                            102L, null, new BigDecimal("100.00"), "LKR",
                            null, "Load benchmark transfer " + index, "123456");

                    TransferResponse resp = transferService.initiateTransfer(
                            key, "123456", req, senderUser, "192.168.1." + (index % 250));

                    long elapsed = System.nanoTime() - reqStart;
                    latenciesNanos.add(elapsed);

                    if (resp != null && resp.status().name().equals("COMPLETED")) {
                        successCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception ex) {
                    errorCount.incrementAndGet();
                    log.error("Transfer error in load test: {}", ex.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startLatch.countDown();
        boolean completedInTime = finishLatch.await(30, TimeUnit.SECONDS);
        long overallDurationNanos = System.nanoTime() - overallStartNanos;

        executor.shutdown();

        assertThat(completedInTime).isTrue();
        assertThat(successCount.get()).isEqualTo(totalRequests);
        assertThat(errorCount.get()).isEqualTo(0);

        // Calculate and log latency statistics
        printBenchmarkSummary("P2P Transfer Load Benchmark", totalRequests, overallDurationNanos, latenciesNanos);
    }

    @Test
    @DisplayName("Concurrency Race Condition: 25 threads fire exact same Idempotency-Key simultaneously")
    void testIdempotencyRaceCondition() throws Exception {
        int threads = 25;
        String sharedKey = "RACE-CONDITION-KEY-" + UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);

        List<TransferResponse> responses = Collections.synchronizedList(new ArrayList<>());

        Map<String, TransactionRecord> inMemoryTransfers = new ConcurrentHashMap<>();
        Object lock = new Object();

        when(transactionStore.findTransferByIdempotencyKey(sharedKey)).thenAnswer(inv ->
                Optional.ofNullable(inMemoryTransfers.get(sharedKey)));

        when(transactionStore.createTransferWithIdempotency(any(TransactionRecord.class), eq(sharedKey), anyString()))
                .thenAnswer(inv -> {
                    synchronized (lock) {
                        TransactionRecord existing = inMemoryTransfers.get(sharedKey);
                        if (existing != null) {
                            return existing;
                        }
                        TransactionRecord r = inv.getArgument(0);
                        TransactionRecord created = new TransactionRecord(
                                9999L, r.reference(), sharedKey,
                                r.senderId(), r.senderPhone(), r.senderFullName(),
                                r.receiverId(), r.receiverPhone(), r.receiverFullName(),
                                r.senderBankCode(), r.receiverBankCode(),
                                r.amount(), r.currency(), TransactionStatus.PENDING,
                                null, null, null, r.note(), true, r.initiatedVia(),
                                null, 1L, Instant.now(), Instant.now()
                        );
                        inMemoryTransfers.put(sharedKey, created);
                        return created;
                    }
                });

        when(transactionStore.transitionTransferStatus(eq(9999L), any(), any(), any(), any(), any(), any(), anyLong()))
                .thenAnswer(inv -> {
                    synchronized (lock) {
                        TransactionStatus status = inv.getArgument(1);
                        String bankTxRef = inv.getArgument(4);
                        String provRef = inv.getArgument(5);
                        TransactionRecord updated = new TransactionRecord(
                                9999L, "uuid-ref-1", sharedKey,
                                101L, "+94771000001", "Kasun Perera [TEST]",
                                102L, "+94771000002", "Nimal Silva [TEST]",
                                "SIMULATED_BANK_B", "SIMULATED_BANK_A",
                                new BigDecimal("250.00"), "LKR", status,
                                null, bankTxRef != null ? bankTxRef : "SIM-TX-9999", provRef != null ? provRef : "SIM-PROV-9999",
                                "Race test", true, TransferInitiatedVia.APP,
                                null, 2L, Instant.now(), Instant.now()
                        );
                        inMemoryTransfers.put(sharedKey, updated);
                        return updated;
                    }
                });

        InitiateTransferRequest req = new InitiateTransferRequest(
                102L, null, new BigDecimal("250.00"), "LKR",
                null, "Race test", "123456");

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // Await race trigger
                    TransferResponse resp = transferService.initiateTransfer(
                            sharedKey, "123456", req, senderUser, "10.0.0.1");
                    responses.add(resp);
                } catch (Exception e) {
                    log.error("Race error: {}", e.getMessage());
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown(); // FIRE ALL AT ONCE
        boolean done = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(done).isTrue();
        assertThat(responses).hasSize(threads);

        // All 25 threads MUST return identical status COMPLETED
        for (TransferResponse resp : responses) {
            assertThat(resp.status().name()).isEqualTo("COMPLETED");
            assertThat(resp.amount()).isEqualByComparingTo("250.00");
        }
    }

    @Test
    @DisplayName("Concurrency Token-Bucket: 40 threads perform concurrent Lookups")
    void testConcurrentLookupRateLimiting() throws Exception {
        int threads = 40;
        LookupRateLimiter limiter = new LookupRateLimiter(50, 100);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);

        AtomicInteger allowed = new AtomicInteger(0);
        AtomicInteger denied = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    // All 40 threads check per-user bucket (capacity = 50) and per-IP bucket (capacity = 100)
                    limiter.consume(101L, "192.168.1." + threadId);
                    allowed.incrementAndGet();
                } catch (RateLimitException rle) {
                    denied.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        endGate.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // 40 requests against capacity of 50 should all succeed without thread contention
        assertThat(allowed.get()).isEqualTo(40);
        assertThat(denied.get()).isEqualTo(0);
    }

    private void printBenchmarkSummary(String scenarioName, int totalRequests, long totalDurationNanos, List<Long> latenciesNanos) {
        Collections.sort(latenciesNanos);

        double totalMs = totalDurationNanos / 1_000_000.0;
        double throughputRps = (totalRequests / (totalDurationNanos / 1_000_000_000.0));

        double minMs = latenciesNanos.get(0) / 1_000_000.0;
        double maxMs = latenciesNanos.get(latenciesNanos.size() - 1) / 1_000_000.0;
        double meanMs = (latenciesNanos.stream().mapToLong(Long::longValue).average().orElse(0)) / 1_000_000.0;
        double p50Ms = latenciesNanos.get((int) (latenciesNanos.size() * 0.50)) / 1_000_000.0;
        double p95Ms = latenciesNanos.get((int) (latenciesNanos.size() * 0.95)) / 1_000_000.0;
        double p99Ms = latenciesNanos.get((int) (latenciesNanos.size() * 0.99)) / 1_000_000.0;

        System.out.println("=================================================================");
        System.out.printf("  BENCHMARK: %s%n", scenarioName);
        System.out.println("=================================================================");
        System.out.printf("  Total Requests:  %d%n", totalRequests);
        System.out.printf("  Total Duration:  %.2f ms%n", totalMs);
        System.out.printf("  Throughput:      %.2f req/sec%n", throughputRps);
        System.out.printf("  Min Latency:     %.2f ms%n", minMs);
        System.out.printf("  Mean Latency:    %.2f ms%n", meanMs);
        System.out.printf("  Median (P50):    %.2f ms%n", p50Ms);
        System.out.printf("  P95 Latency:     %.2f ms%n", p95Ms);
        System.out.printf("  P99 Latency:     %.2f ms%n", p99Ms);
        System.out.printf("  Max Latency:     %.2f ms%n", maxMs);
        System.out.println("=================================================================");
    }
}
