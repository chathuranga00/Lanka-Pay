package com.lankapay.load;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.common.exception.RateLimitException;
import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.adapter.BankAdapter;
import com.lankapay.bank.adapter.BankAdapterRegistry;
import com.lankapay.bank.adapter.simulated.*;
import com.lankapay.bank.domain.BankAccount;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.repository.BankAccountRepository;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.repository.UserRepository;
import com.lankapay.registry.repository.LookupAbuseCounterRepository;
import com.lankapay.registry.repository.RegistryRepository;
import com.lankapay.registry.service.LookupRateLimiter;
import com.lankapay.registry.service.LookupService;
import com.lankapay.transfer.domain.Transfer;
import com.lankapay.transfer.repository.DailyLimitRepository;
import com.lankapay.transfer.repository.IdempotencyRecordRepository;
import com.lankapay.transfer.repository.TransactionEventRepository;
import com.lankapay.transfer.repository.TransferRepository;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock TransferRepository transferRepository;
    @Mock TransactionEventRepository transactionEventRepository;
    @Mock BankAccountRepository bankAccountRepository;
    @Mock UserRepository userRepository;
    @Mock RegistryRepository registryRepository;
    @Mock DailyLimitRepository dailyLimitRepository;
    @Mock IdempotencyRecordRepository idempotencyRecordRepository;
    @Mock LookupAbuseCounterRepository abuseCounterRepository;
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
    private BankAccount senderAccount;
    private BankAccount receiverAccount;

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

        dailyLimitService = new DailyLimitService(dailyLimitRepository);
        transferProcessor = new TransferProcessor(
                transferRepository, transactionEventRepository, bankAdapterRegistry, dailyLimitService, auditService);
        idempotencyService = new IdempotencyService(idempotencyRecordRepository, objectMapper);

        transferService = new TransferService(
                transferRepository, bankAccountRepository, userRepository,
                transferProcessor, dailyLimitService, idempotencyService, passwordEncoder);

        // Sender
        senderUser = new User("+94771000001", "Kasun Perera [TEST]", "199010000001", passwordEncoder.encode("123456"));
        senderUser.setStatus(UserStatus.ACTIVE);
        senderUser.setRole(UserRole.CUSTOMER);
        ReflectionTestUtils.setField(senderUser, "id", 101L);

        senderAccount = new BankAccount(senderUser, "SIMULATED_BANK_B", "hashB", "****1001", "Kasun Perera [TEST]", "encRefB");
        senderAccount.setDefault(true);
        senderAccount.setStatus(BankAccountStatus.ACTIVE);
        ReflectionTestUtils.setField(senderAccount, "id", 202L);

        // Receiver
        receiverUser = new User("+94771000002", "Nimal Silva [TEST]", "199010000002", passwordEncoder.encode("123456"));
        receiverUser.setStatus(UserStatus.ACTIVE);
        receiverUser.setRole(UserRole.CUSTOMER);
        ReflectionTestUtils.setField(receiverUser, "id", 102L);

        receiverAccount = new BankAccount(receiverUser, "SIMULATED_BANK_A", "hashA", "****1002", "Nimal Silva [TEST]", "encRefA");
        receiverAccount.setDefault(true);
        receiverAccount.setStatus(BankAccountStatus.ACTIVE);
        ReflectionTestUtils.setField(receiverAccount, "id", 204L);

        // Stubs
        when(userRepository.findById(102L)).thenReturn(Optional.of(receiverUser));
        when(bankAccountRepository.findByUserIdAndIsDefaultTrueAndStatus(101L, BankAccountStatus.ACTIVE))
                .thenReturn(Optional.of(senderAccount));
        when(bankAccountRepository.findByUserIdAndIsDefaultTrueAndStatus(102L, BankAccountStatus.ACTIVE))
                .thenReturn(Optional.of(receiverAccount));

        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            if (t.getId() == null) {
                ReflectionTestUtils.setField(t, "id", Math.abs(UUID.randomUUID().getMostSignificantBits() % 100000L));
            }
            return t;
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
                            key, null, req, senderUser, "192.168.1." + (index % 250));

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
        AtomicInteger saveCount = new AtomicInteger(0);

        // When transfer is saved for the first time, store it in in-memory map
        Map<String, Transfer> inMemoryTransfers = new ConcurrentHashMap<>();
        when(transferRepository.findByIdempotencyKey(sharedKey)).thenAnswer(inv ->
                Optional.ofNullable(inMemoryTransfers.get(sharedKey)));

        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> {
            Transfer t = inv.getArgument(0);
            if (t.getId() == null) {
                ReflectionTestUtils.setField(t, "id", 9999L);
            }
            if (t.getIdempotencyKey() != null) {
                inMemoryTransfers.putIfAbsent(t.getIdempotencyKey(), t);
                saveCount.incrementAndGet();
            }
            return t;
        });

        InitiateTransferRequest req = new InitiateTransferRequest(
                102L, null, new BigDecimal("250.00"), "LKR",
                null, "Race test", "123456");

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // Await race trigger
                    TransferResponse resp = transferService.initiateTransfer(
                            sharedKey, null, req, senderUser, "10.0.0.1");
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
