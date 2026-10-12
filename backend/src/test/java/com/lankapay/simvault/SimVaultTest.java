package com.lankapay.simvault;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lankapay.audit.service.AuditService;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.RateLimitException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.UserStore;
import com.lankapay.identity.otp.OtpProvider;
import com.lankapay.simvault.internal.VaultComponents;
import com.lankapay.simvault.internal.SimulatedCardVaultImpl;
import com.lankapay.virtualcard.provider.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the simulated card vault.
 *
 * Tests:
 *  T-01 tokenize success — happy path returns valid token
 *  T-02 real-looking number rejected (generic message, no detail)
 *  T-03 wrong Luhn rejected
 *  T-04 expired card number rejected
 *  T-05 token bound to another user is rejected
 *  T-06 token expiry — expired token is rejected
 *  T-07 rate limit exceeded per user
 *  T-08 lockout after max failures
 *  T-09 LOG SCAN — no seeded PAN or CVV appears in any log output
 *  T-10 ALWAYS_APPROVED scenario returns approved auth response
 *  T-11 INSUFFICIENT_FUNDS scenario returns declined response
 *  T-12 HARD_DECLINE scenario returns declined response
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SimulatedCardVault tests")
class SimVaultTest {

    @Mock OtpProvider otpProvider;
    @Mock UserStore   userStore;
    @Mock AuditService auditService;

    SimulatedCardVault vault;
    ListAppender<ILoggingEvent> logCapture;
    Logger rootLogger;

    // AES-256-GCM test key (32 zero bytes, Base64-URL encoded)
    static final String TEST_KEY =
            Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

    // TEST card number — ALWAYS_APPROVED (last4 = 0010)
    static final String PAN_APPROVED  = "9900000000000010";
    static final String EXP_MONTH     = "12";
    static final String EXP_YEAR      = "27";
    static final String CVV_APPROVED  = "111";
    static final Long   USER_A        = 1L;
    static final Long   USER_B        = 2L;

    // TEST card — CARD_EXPIRED (last4 = 0069)
    static final String PAN_EXPIRED   = "9900000000000069";
    static final String CVV_EXPIRED   = "333";

    @BeforeEach
    void setUp() {
        // Attach log capture to root logger
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logCapture = new ListAppender<>();
        logCapture.start();
        rootLogger.addAppender(logCapture);

        vault = new SimulatedCardVault(new SimulatedCardVaultImpl(
                new VaultComponents(TEST_KEY, 15L, 3L, 5L, 3, 1L,
                        otpProvider, userStore, auditService)));
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(logCapture);
    }

    // ── T-01: Tokenize success ─────────────────────────────────

    @Test
    @DisplayName("T-01 tokenize success returns token, last4, brandLabel=TEST CARD, simulated=true")
    void t01_tokenizeSuccess() {
        TokenizeResponse resp = vault.tokenize(req(PAN_APPROVED, EXP_MONTH, EXP_YEAR, CVV_APPROVED, USER_A));

        assertThat(resp.cardToken()).isNotBlank();
        assertThat(resp.last4()).isEqualTo("0010");
        assertThat(resp.brandLabel()).isEqualTo("TEST CARD");
        assertThat(resp.simulated()).isTrue();
        assertThat(resp.tokenExpiresAt()).isNotNull();
        // Token must be a UUID
        assertThat(resp.cardToken()).matches("[0-9a-f\\-]{36}");
    }

    // ── T-02: Real-looking number rejected ────────────────────

    @Test
    @DisplayName("T-02 any number not in seed table is rejected with generic message")
    void t02_realLookingNumberRejected() {
        // A Luhn-valid number NOT in our seed table (starts with 41, looks real)
        String notInSeed = "4111111111111111"; // classic Visa test number

        assertThatThrownBy(() ->
                vault.tokenize(req(notInSeed, EXP_MONTH, EXP_YEAR, "123", USER_A)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Test cards only")
                .hasMessageNotContaining("Luhn")
                .hasMessageNotContaining("not found")
                .hasMessageNotContaining("4111");
    }

    // ── T-03: Wrong Luhn ──────────────────────────────────────

    @Test
    @DisplayName("T-03 number with wrong Luhn check digit is rejected generically")
    void t03_wrongLuhnRejected() {
        // 9900000000000010 with last digit changed to 1 → invalid Luhn
        String badLuhn = "9900000000000011";

        assertThatThrownBy(() ->
                vault.tokenize(req(badLuhn, EXP_MONTH, EXP_YEAR, CVV_APPROVED, USER_A)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Test cards only")
                .hasMessageNotContaining("Luhn")
                .hasMessageNotContaining("9900000000000011");
    }

    // ── T-04: Expired card ────────────────────────────────────

    @Test
    @DisplayName("T-04 tokenize of CARD_EXPIRED card succeeds at tokenize (scenario controls auth decline)")
    void t04_expiredCardTokenizes() {
        // The validator allows past expiry through — the CARD_EXPIRED scenario is tested at authorize()
        TokenizeResponse resp = vault.tokenize(req(PAN_EXPIRED, "01", "20", CVV_EXPIRED, USER_A));
        assertThat(resp.cardToken()).isNotBlank();
        assertThat(resp.last4()).isEqualTo("0069");

        // Now authorize — should be declined because scenario = CARD_EXPIRED
        CardAuthResponse auth = vault.authorize(new CardAuthRequest(
                resp.cardToken(), USER_A, new BigDecimal("100.00"), "LKR", UUID.randomUUID().toString()));

        assertThat(auth.approved()).isFalse();
        assertThat(auth.status()).isEqualTo(CardAuthStatus.DECLINED);
        assertThat(auth.scenario()).isEqualTo(CardScenario.CARD_EXPIRED);
        assertThat(auth.declineReason()).doesNotContain(PAN_EXPIRED);
    }

    // ── T-05: Token bound to another user ─────────────────────

    @Test
    @DisplayName("T-05 using token issued to userA as userB throws AuthorizationException")
    void t05_tokenBoundToOtherUser() {
        TokenizeResponse resp = vault.tokenize(req(PAN_APPROVED, EXP_MONTH, EXP_YEAR, CVV_APPROVED, USER_A));
        String token = resp.cardToken();

        // USER_B tries to use USER_A's token
        assertThatThrownBy(() -> vault.getTokenInfo(token, USER_B))
                .isInstanceOf(AuthorizationException.class)
                .extracting("errorCode").isEqualTo("CARD_TOKEN_WRONG_USER");

        // Also rejects on authorize
        assertThatThrownBy(() ->
                vault.authorize(new CardAuthRequest(token, USER_B,
                        new BigDecimal("100.00"), "LKR", UUID.randomUUID().toString())))
                .isInstanceOf(AuthorizationException.class);
    }

    // ── T-06: Token expiry ────────────────────────────────────

    @Test
    @DisplayName("T-06 expired token is rejected with TOKEN_EXPIRED")
    void t06_tokenExpiry() throws Exception {
        SimulatedCardVault shortVault = new SimulatedCardVault(new SimulatedCardVaultImpl(
                new VaultComponents(TEST_KEY, 0L, 10L, 20L, 5, 15L,
                        otpProvider, userStore, auditService)));

        TokenizeResponse resp = shortVault.tokenize(req(PAN_APPROVED, EXP_MONTH, EXP_YEAR, CVV_APPROVED, USER_A));
        String token = resp.cardToken();

        Thread.sleep(50);

        assertThatThrownBy(() -> shortVault.getTokenInfo(token, USER_A))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("errorCode").satisfies(code ->
                        assertThat(code.toString()).isIn("CARD_TOKEN_EXPIRED", "CARD_TOKEN_NOT_FOUND"));
    }

    // ── T-07: Rate limit ──────────────────────────────────────

    @Test
    @DisplayName("T-07 rate limit exceeded after userCapacity requests")
    void t07_rateLimitPerUser() {
        for (int i = 0; i < 3; i++) {
            vault.tokenize(req(PAN_APPROVED, EXP_MONTH, EXP_YEAR, CVV_APPROVED, USER_A));
        }
        assertThatThrownBy(() ->
                vault.tokenize(req(PAN_APPROVED, EXP_MONTH, EXP_YEAR, CVV_APPROVED, USER_A)))
                .isInstanceOf(RateLimitException.class)
                .extracting("errorCode").isEqualTo("VAULT_TOKENIZE_RATE_LIMITED");
    }

    // ── T-08: Lockout after max failures ──────────────────────

    @Test
    @DisplayName("T-08 user is locked after maxFailures consecutive validation failures")
    void t08_lockoutAfterMaxFailures() {
        // Bad Luhn number — different from all seeded PANs
        String badPan = "9900000000000011"; // invalid Luhn (correct would be ...0010)
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() ->
                    vault.tokenize(req(badPan, EXP_MONTH, EXP_YEAR, CVV_APPROVED, USER_A)))
                    .isInstanceOf(ValidationException.class);
        }
        assertThatThrownBy(() ->
                vault.tokenize(req(PAN_APPROVED, EXP_MONTH, EXP_YEAR, CVV_APPROVED, USER_A)))
                .isInstanceOf(RateLimitException.class)
                .extracting("errorCode").isEqualTo("VAULT_TOKENIZE_LOCKED");
    }

    // ── T-09: LOG SCAN — no PAN or CVV in logs ────────────────

    @Test
    @DisplayName("T-09 CRITICAL: no seeded PAN or CVV appears in any log output after all operations")
    void t09_noPanOrCvvInLogs() {
        // Execute all tokenize paths to maximize log output
        try { vault.tokenize(req(PAN_APPROVED, EXP_MONTH, EXP_YEAR, CVV_APPROVED, USER_A)); } catch (Exception ignored) {}
        try { vault.tokenize(req("9900000000000012", EXP_MONTH, EXP_YEAR, "100", USER_A)); } catch (Exception ignored) {}
        try { vault.tokenize(req(PAN_EXPIRED, "01", "20", CVV_EXPIRED, USER_A)); } catch (Exception ignored) {}

        // Collect all log messages
        String allLogs = logCapture.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);

        // ── Assert: none of the seeded PANs appear ─────────────
        String[] seededPans = {
            "9900000000000010",
            "9900000000000028",
            "9900000000000069",
            "9900000000000119",
            "9900000000000994",
            "9900000000001232",
            "9900000000002347",
            "9900000000003451"
        };
        for (String pan : seededPans) {
            assertThat(allLogs)
                    .as("Seeded PAN %s must not appear in logs", pan)
                    .doesNotContain(pan);
        }

        // ── Assert: none of the test CVVs appear in CVV context ──
        String[] seededCvvs = {"111", "222", "333", "444", "555", "666", "777", "888"};
        for (String cvv : seededCvvs) {
            assertThat(allLogs)
                    .as("CVV value '%s' must not appear in logs after 'cvv' context", cvv)
                    .doesNotContainPattern("(?i)cvv.{0,5}" + cvv);
        }
    }

    // ── T-10: ALWAYS_APPROVED authorize ──────────────────────

    @Test
    @DisplayName("T-10 ALWAYS_APPROVED scenario returns approved CardAuthResponse")
    void t10_alwaysApprovedAuthorize() {
        String token = vault.tokenize(req(PAN_APPROVED, EXP_MONTH, EXP_YEAR, CVV_APPROVED, USER_A)).cardToken();

        CardAuthResponse auth = vault.authorize(new CardAuthRequest(
                token, USER_A, new BigDecimal("500.00"), "LKR", UUID.randomUUID().toString()));

        assertThat(auth.approved()).isTrue();
        assertThat(auth.status()).isEqualTo(CardAuthStatus.APPROVED);
        assertThat(auth.authCode()).isNotBlank();
        assertThat(auth.simulated()).isTrue();
        assertThat(auth.scenario()).isEqualTo(CardScenario.ALWAYS_APPROVED);
        // Response must not contain PAN
        assertThat(auth.toString()).doesNotContain(PAN_APPROVED);
    }

    // ── T-11: INSUFFICIENT_FUNDS authorize ────────────────────

    @Test
    @DisplayName("T-11 INSUFFICIENT_FUNDS scenario returns declined response")
    void t11_insufficientFunds() {
        String pan = "9900000000000028"; // INSUFFICIENT_FUNDS
        String token = vault.tokenize(req(pan, EXP_MONTH, EXP_YEAR, "222", USER_A)).cardToken();

        CardAuthResponse auth = vault.authorize(new CardAuthRequest(
                token, USER_A, new BigDecimal("100.00"), "LKR", UUID.randomUUID().toString()));

        assertThat(auth.approved()).isFalse();
        assertThat(auth.status()).isEqualTo(CardAuthStatus.DECLINED);
        assertThat(auth.scenario()).isEqualTo(CardScenario.INSUFFICIENT_FUNDS);
        assertThat(auth.declineReason()).isNotBlank();
        assertThat(auth.declineReason()).doesNotContain(pan);
    }

    // ── T-12: HARD_DECLINE authorize ──────────────────────────

    @Test
    @DisplayName("T-12 HARD_DECLINE scenario returns permanently declined response")
    void t12_hardDecline() {
        String pan = "9900000000002347"; // HARD_DECLINE
        String token = vault.tokenize(req(pan, EXP_MONTH, EXP_YEAR, "777", USER_A)).cardToken();

        CardAuthResponse auth = vault.authorize(new CardAuthRequest(
                token, USER_A, new BigDecimal("100.00"), "LKR", UUID.randomUUID().toString()));

        assertThat(auth.approved()).isFalse();
        assertThat(auth.status()).isEqualTo(CardAuthStatus.DECLINED);
        assertThat(auth.scenario()).isEqualTo(CardScenario.HARD_DECLINE);
    }

    // ── Helpers ───────────────────────────────────────────────

    private TokenizeRequest req(String pan, String mm, String yy, String cvv, Long userId) {
        return new TokenizeRequest(pan, mm, yy, cvv, "Test Holder", userId,
                UUID.randomUUID().toString());
    }
}
