package com.lankapay.bank.adapter.simulated;

import com.lankapay.bank.crypto.AccountReferenceEncryptor;
import com.lankapay.bank.domain.AccountConsent;
import com.lankapay.bank.domain.BankAccount;
import com.lankapay.bank.domain.BankAccountStatus;
import com.lankapay.bank.domain.ConsentType;
import com.lankapay.bank.repository.AccountConsentRepository;
import com.lankapay.bank.repository.BankAccountRepository;
import com.lankapay.bank.repository.BankInstitutionRepository;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.repository.UserRepository;
import com.lankapay.registry.domain.Registry;
import com.lankapay.registry.repository.RegistryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Automatically seeds standard test users and bank accounts when running in SIMULATED mode.
 *
 * ⚠ SIMULATED ONLY — deactivated if app.bank-adapter.mode != SIMULATED.
 *
 * Seeded test personas:
 *  1. Kasun Perera [TEST] (+94771000001) – 3 accounts at Banks A, B, and C (Default: Bank B)
 *  2. Nimal Silva [TEST]   (+94771000002) – 1 account at Bank A (Default: Bank A)
 *  3. Fail Scenario User [TEST] (+94771000003) – Account triggering HARD_FAILURE (100400)
 *  4. Timeout Scenario User [TEST] (+94771000004) – Account triggering TIMEOUT_SUCCEEDED (100200)
 *  5. Reversal Scenario User [TEST] (+94771000005) – Account triggering LATER_REVERSAL (100500)
 *  6. Operations Administrator [TEST] (+94770000001) – Role: ADMIN
 *
 * All PINs are set to "123456" for demo and automated testing convenience.
 */
@Component
@ConditionalOnProperty(name = "app.bank-adapter.mode", havingValue = "SIMULATED", matchIfMissing = true)
public class SimulatedDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulatedDataSeeder.class);

    private final UserRepository userRepository;
    private final RegistryRepository registryRepository;
    private final BankAccountRepository bankAccountRepository;
    private final AccountConsentRepository consentRepository;
    private final BankInstitutionRepository institutionRepository;
    private final AccountReferenceEncryptor encryptor;
    private final PasswordEncoder passwordEncoder;

    public SimulatedDataSeeder(UserRepository userRepository,
                               RegistryRepository registryRepository,
                               BankAccountRepository bankAccountRepository,
                               AccountConsentRepository consentRepository,
                               BankInstitutionRepository institutionRepository,
                               AccountReferenceEncryptor encryptor,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.registryRepository = registryRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.consentRepository = consentRepository;
        this.institutionRepository = institutionRepository;
        this.encryptor = encryptor;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("[SIMULATED] Checking database for simulated demo test accounts...");

        // Ensure Admin user exists
        seedUserIfAbsent("+94770000001", "Operations Administrator [TEST]", "198000000001", UserRole.ADMIN, null);

        // 1. Kasun Perera [TEST] – 3 accounts at Banks A/B/C, default B
        User kasun = seedUserIfAbsent("+94771000001", "Kasun Perera [TEST]", "199010000001", UserRole.CUSTOMER, user -> {
            linkAccount(user, "SIMULATED_BANK_A", "101001", "Kasun Perera [TEST]", "Savings Account A [TEST]", false);
            linkAccount(user, "SIMULATED_BANK_B", "201001", "Kasun Perera [TEST]", "Salary Account B [TEST]", true); // Default B
            linkAccount(user, "SIMULATED_BANK_C", "301001", "Kasun Perera [TEST]", "Investment Account C [TEST]", false);
        });

        // 2. Nimal Silva [TEST] – 1 account at Bank A
        seedUserIfAbsent("+94771000002", "Nimal Silva [TEST]", "199010000002", UserRole.CUSTOMER, user -> {
            linkAccount(user, "SIMULATED_BANK_A", "101002", "Nimal Silva [TEST]", "Primary Account [TEST]", true);
        });

        // 3. Scenario account: Failure (Bank A, account 100400 triggers HARD_FAILURE)
        seedUserIfAbsent("+94771000003", "Fail Scenario User [TEST]", "199010000003", UserRole.CUSTOMER, user -> {
            linkAccount(user, "SIMULATED_BANK_A", "100400", "Fail Account [TEST]", "Hard Failure Account [TEST]", true);
        });

        // 4. Scenario account: Timeout (Bank A, account 100200 triggers TIMEOUT_SUCCEEDED)
        seedUserIfAbsent("+94771000004", "Timeout Scenario User [TEST]", "199010000004", UserRole.CUSTOMER, user -> {
            linkAccount(user, "SIMULATED_BANK_A", "100200", "Timeout Account [TEST]", "Timeout Trigger Account [TEST]", true);
        });

        // 5. Scenario account: Reversal (Bank A, account 100500 triggers LATER_REVERSAL)
        seedUserIfAbsent("+94771000005", "Reversal Scenario User [TEST]", "199010000005", UserRole.CUSTOMER, user -> {
            linkAccount(user, "SIMULATED_BANK_A", "100500", "Reversal Account [TEST]", "Reversal Trigger Account [TEST]", true);
        });

        log.info("[SIMULATED] Demo test accounts verification completed successfully.");
    }

    private User seedUserIfAbsent(String phone, String fullName, String nic, UserRole role, AccountInitializer onInit) {
        return userRepository.findByPhone(phone).orElseGet(() -> {
            User newUser = new User(phone, fullName, nic, passwordEncoder.encode("123456"));
            newUser.setStatus(UserStatus.ACTIVE);
            newUser.setRole(role);
            User saved = userRepository.save(newUser);

            // Register in registry if CUSTOMER
            if (role == UserRole.CUSTOMER) {
                Registry reg = new Registry(saved, phone);
                reg.setDiscoverable(true);
                registryRepository.save(reg);
            }

            if (onInit != null) {
                onInit.initialize(saved);
            }

            log.info("[SIMULATED] Seeded test user: phone={}, name='{}', role={}", phone, fullName, role);
            return saved;
        });
    }

    private void linkAccount(User user, String bankCode, String accountNumber,
                             String accountName, String label, boolean isDefault) {
        String hash = sha256(bankCode + accountNumber);
        if (bankAccountRepository.findByAccountNumberHash(hash).isPresent()) {
            return;
        }

        String masked = maskAccountNumber(accountNumber);
        String encryptedRef = encryptor.encrypt(accountNumber);

        BankAccount account = new BankAccount(user, bankCode, hash, masked, accountName, encryptedRef);
        account.setLabel(label);
        account.setDefault(isDefault);
        account.setDiscoverable(true);
        account.setReceivingEnabled(true);
        account.setStatus(BankAccountStatus.ACTIVE);

        BankAccount saved = bankAccountRepository.save(account);

        AccountConsent consent = new AccountConsent(
                user, saved, ConsentType.LINK_ACCOUNT, true, "127.0.0.1");
        consentRepository.save(consent);

        log.info("[SIMULATED] Linked test bank account: user={}, bank={}, masked={}, default={}",
                user.getId(), bankCode, masked, isDefault);
    }

    private static String maskAccountNumber(String acc) {
        if (acc == null || acc.length() < 4) return "****";
        return "****" + acc.substring(acc.length() - 4);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @FunctionalInterface
    private interface AccountInitializer {
        void initialize(User user);
    }
}
