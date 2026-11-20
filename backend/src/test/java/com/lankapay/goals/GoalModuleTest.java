package com.lankapay.goals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.InvalidCredentialsException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.goals.domain.*;
import com.lankapay.goals.service.GoalService;
import com.lankapay.goals.store.InMemoryGoalStore;
import com.lankapay.goals.web.dto.*;
import com.lankapay.identity.domain.UserAccount;
import com.lankapay.identity.domain.UserStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoalModuleTest {

    private InMemoryGoalStore goalStore;
    private UserStore userStore;
    private PasswordEncoder passwordEncoder;
    private ObjectMapper objectMapper;
    private GoalService goalService;

    private static final Long USER_1 = 101L;
    private static final Long USER_2 = 102L;
    private static final String VALID_PIN = "123456";
    private static final String INVALID_PIN = "000000";

    @BeforeEach
    void setUp() {
        goalStore = new InMemoryGoalStore();
        userStore = mock(UserStore.class);
        passwordEncoder = mock(PasswordEncoder.class);
        objectMapper = new ObjectMapper();

        UserAccount user1Account = mock(UserAccount.class);
        when(user1Account.id()).thenReturn(USER_1);
        when(user1Account.passwordHash()).thenReturn("hashed_pin_101");
        when(userStore.findUserById(USER_1)).thenReturn(Optional.of(user1Account));

        UserAccount user2Account = mock(UserAccount.class);
        when(user2Account.id()).thenReturn(USER_2);
        when(user2Account.passwordHash()).thenReturn("hashed_pin_102");
        when(userStore.findUserById(USER_2)).thenReturn(Optional.of(user2Account));

        when(passwordEncoder.matches(eq(VALID_PIN), any())).thenReturn(true);
        when(passwordEncoder.matches(eq(INVALID_PIN), any())).thenReturn(false);

        goalService = new GoalService(goalStore, userStore, passwordEncoder, objectMapper, null);
    }

    @Test
    @DisplayName("Create goal: exact amounts, SIMULATED label, initial state")
    void createGoalSuccess() {
        CreateGoalRequest req = new CreateGoalRequest(
                "Emergency Fund",
                500_000L, // 5,000.00 LKR in cents
                LocalDate.now().plusMonths(6),
                "ACC-1001",
                0L,
                null
        );

        GoalDto created = goalService.createGoal(USER_1, req, null, "127.0.0.1");

        assertThat(created).isNotNull();
        assertThat(created.name()).isEqualTo("Emergency Fund");
        assertThat(created.targetMinor()).isEqualTo(500_000L);
        assertThat(created.savedMinor()).isEqualTo(0L);
        assertThat(created.status()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(created.ledgerType()).isEqualTo("SIMULATED");
        assertThat(created.progressPercentage()).isEqualTo(0.0);

        List<GoalDto> list = goalService.listGoals(USER_1);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).id()).isEqualTo(created.id());
    }

    @Test
    @DisplayName("Create goal with initial deposit: appends ledger entry and verifies step-up PIN")
    void createGoalWithInitialDeposit() {
        CreateGoalRequest req = new CreateGoalRequest(
                "New Laptop",
                1_000_000L,
                LocalDate.now().plusMonths(3),
                "ACC-1001",
                250_000L,
                VALID_PIN
        );

        GoalDto created = goalService.createGoal(USER_1, req, null, "127.0.0.1");
        assertThat(created.savedMinor()).isEqualTo(250_000L);
        assertThat(created.progressPercentage()).isEqualTo(25.0);

        GoalDetailsDto details = goalService.getGoal(USER_1, created.id());
        assertThat(details.entries()).hasSize(1);
        assertThat(details.entries().get(0).type()).isEqualTo(GoalLedgerEntryType.CONTRIBUTION);
        assertThat(details.entries().get(0).amountMinor()).isEqualTo(250_000L);
        assertThat(details.entries().get(0).balanceAfterMinor()).isEqualTo(250_000L);
        assertThat(details.entries().get(0).ledgerType()).isEqualTo("SIMULATED");
        assertThat(details.disclaimer()).contains("SIMULATED");
    }

    @Test
    @DisplayName("Limits: target must be positive, initial deposit cannot exceed target")
    void limitValidationsOnCreate() {
        CreateGoalRequest negativeTarget = new CreateGoalRequest(
                "Invalid Target", -100L, null, "ACC-1001", 0L, null);
        assertThatThrownBy(() -> goalService.createGoal(USER_1, negativeTarget, null, "127.0.0.1"))
                .isInstanceOf(ValidationException.class);

        CreateGoalRequest excessiveInitial = new CreateGoalRequest(
                "Excessive Initial", 10_000L, null, "ACC-1001", 20_000L, VALID_PIN);
        assertThatThrownBy(() -> goalService.createGoal(USER_1, excessiveInitial, null, "127.0.0.1"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Limits: cannot withdraw more than current savedMinor")
    void withdrawLimitEnforced() {
        GoalDto goal = goalService.createGoal(USER_1, new CreateGoalRequest(
                "Vacation", 100_000L, null, "ACC-1001", 30_000L, VALID_PIN), null, "127.0.0.1");

        GoalTransactionRequest req = new GoalTransactionRequest(40_000L, "Too much", VALID_PIN);
        assertThatThrownBy(() -> goalService.withdraw(USER_1, goal.id(), req, null, null, "127.0.0.1"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Cannot withdraw");
    }

    @Test
    @DisplayName("Step-up authentication: missing or invalid PIN is rejected")
    void stepUpAuthEnforced() {
        GoalDto goal = goalService.createGoal(USER_1, new CreateGoalRequest(
                "Bike", 100_000L, null, "ACC-1001", 0L, null), null, "127.0.0.1");

        // Missing PIN
        GoalTransactionRequest missingPinReq = new GoalTransactionRequest(10_000L, "Save", null);
        assertThatThrownBy(() -> goalService.contribute(USER_1, goal.id(), missingPinReq, null, null, "127.0.0.1"))
                .isInstanceOf(com.lankapay.common.exception.AuthorizationException.class);

        // Invalid PIN
        GoalTransactionRequest invalidPinReq = new GoalTransactionRequest(10_000L, "Save", INVALID_PIN);
        assertThatThrownBy(() -> goalService.contribute(USER_1, goal.id(), invalidPinReq, null, null, "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("Access Control: User cannot view, update, delete, contribute or withdraw other user's goal")
    void accessControlStrictlyEnforced() {
        GoalDto user1Goal = goalService.createGoal(USER_1, new CreateGoalRequest(
                "Secret Fund", 200_000L, null, "ACC-1001", 50_000L, VALID_PIN), null, "127.0.0.1");

        // USER_2 cannot read USER_1's goal
        assertThatThrownBy(() -> goalService.getGoal(USER_2, user1Goal.id()))
                .isInstanceOf(ResourceNotFoundException.class);

        // USER_2 cannot contribute to USER_1's goal
        GoalTransactionRequest contReq = new GoalTransactionRequest(10_000L, "Test", VALID_PIN);
        assertThatThrownBy(() -> goalService.contribute(USER_2, user1Goal.id(), contReq, null, null, "127.0.0.1"))
                .isInstanceOf(ResourceNotFoundException.class);

        // USER_2 cannot withdraw from USER_1's goal
        GoalTransactionRequest withReq = new GoalTransactionRequest(5_000L, "Test", VALID_PIN);
        assertThatThrownBy(() -> goalService.withdraw(USER_2, user1Goal.id(), withReq, null, null, "127.0.0.1"))
                .isInstanceOf(ResourceNotFoundException.class);

        // USER_2 cannot update USER_1's goal
        UpdateGoalRequest updReq = new UpdateGoalRequest("Hacked", 500_000L, null, null, null);
        assertThatThrownBy(() -> goalService.updateGoal(USER_2, user1Goal.id(), updReq, "127.0.0.1"))
                .isInstanceOf(ResourceNotFoundException.class);

        // USER_2 cannot delete USER_1's goal
        assertThatThrownBy(() -> goalService.deleteGoal(USER_2, user1Goal.id(), "127.0.0.1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Idempotency: duplicate key with same parameters returns cached; conflicting parameters throws 409")
    void idempotencyHandling() {
        GoalDto goal = goalService.createGoal(USER_1, new CreateGoalRequest(
                "Car", 1_000_000L, null, "ACC-1001", 0L, null), null, "127.0.0.1");

        String idempKey = "KEY-GOAL-123";
        GoalTransactionRequest req = new GoalTransactionRequest(50_000L, "First pay", VALID_PIN);

        GoalTransactionResponse res1 = goalService.contribute(USER_1, goal.id(), req, idempKey, null, "127.0.0.1");
        assertThat(res1.goal().savedMinor()).isEqualTo(50_000L);

        // Replaying same key and same payload returns cached without double credit
        GoalTransactionResponse res2 = goalService.contribute(USER_1, goal.id(), req, idempKey, null, "127.0.0.1");
        assertThat(res2.goal().savedMinor()).isEqualTo(50_000L);
        assertThat(res2.entry().id()).isEqualTo(res1.entry().id());

        // Same key with different amount throws ConflictException
        GoalTransactionRequest diffReq = new GoalTransactionRequest(75_000L, "Different pay", VALID_PIN);
        assertThatThrownBy(() -> goalService.contribute(USER_1, goal.id(), diffReq, idempKey, null, "127.0.0.1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Exact amounts & Append-Only Ledger: multiple contributions and withdrawals update balance precisely")
    void exactAmountsAndLedgerHistory() {
        GoalDto goal = goalService.createGoal(USER_1, new CreateGoalRequest(
                "Wedding", 2_000_000L, null, "ACC-1001", 0L, null), null, "127.0.0.1");

        // Contribute 123,456 minor units
        goalService.contribute(USER_1, goal.id(), new GoalTransactionRequest(123_456L, "Part 1", VALID_PIN), "K1", null, "127.0.0.1");

        // Contribute 654,321 minor units
        goalService.contribute(USER_1, goal.id(), new GoalTransactionRequest(654_321L, "Part 2", VALID_PIN), "K2", null, "127.0.0.1");

        // Withdraw 77,777 minor units
        goalService.withdraw(USER_1, goal.id(), new GoalTransactionRequest(77_777L, "Expense", VALID_PIN), "K3", null, "127.0.0.1");

        // Exact expected: 123456 + 654321 - 77777 = 700,000 minor units
        GoalDetailsDto details = goalService.getGoal(USER_1, goal.id());
        assertThat(details.goal().savedMinor()).isEqualTo(700_000L);
        assertThat(details.entries()).hasSize(3);

        // Verify append-only entries list
        assertThat(details.entries().get(0).balanceAfterMinor()).isEqualTo(700_000L);
        assertThat(details.entries().get(0).type()).isEqualTo(GoalLedgerEntryType.WITHDRAWAL);
        assertThat(details.entries().get(0).amountMinor()).isEqualTo(77_777L);
    }

    @Test
    @DisplayName("Concurrency: parallel contributions maintain exact balance without race conditions")
    void concurrentContributionsSafety() throws InterruptedException {
        GoalDto goal = goalService.createGoal(USER_1, new CreateGoalRequest(
                "High Concurrency Test", 10_000_000L, null, "ACC-1001", 0L, null), null, "127.0.0.1");

        int numThreads = 20;
        long contributionPerThread = 15_000L; // 20 * 15,000 = 300,000 minor units
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    goalService.contribute(
                            USER_1,
                            goal.id(),
                            new GoalTransactionRequest(contributionPerThread, "Thread " + index, VALID_PIN),
                            "CONCURRENCY-KEY-" + index,
                            null,
                            "127.0.0.1"
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(numThreads);

        GoalDetailsDto details = goalService.getGoal(USER_1, goal.id());
        long expectedTotal = numThreads * contributionPerThread;
        assertThat(details.goal().savedMinor()).isEqualTo(expectedTotal);
        assertThat(details.entries()).hasSize(numThreads);
    }
}
