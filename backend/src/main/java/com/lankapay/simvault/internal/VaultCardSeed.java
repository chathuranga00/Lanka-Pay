package com.lankapay.simvault.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Seeds the 8 deterministic TEST cards into the vault store.
 *
 * ISOLATION: package-private — never exported outside com.lankapay.simvault.
 *
 * ⚠ TEST DATA ONLY — all numbers are Luhn-valid 99xx numbers that cannot
 * belong to any real card network (99 prefix is not a valid BIN range).
 * See docs/test-cards.md for the full table.
 *
 * The PANs are stored AES-GCM encrypted at rest (via VaultEncryptor).
 * CVVs are BCrypt-hashed; original CVV values are discarded after hashing.
 */
final class VaultCardSeed {

    private static final Logger log = LoggerFactory.getLogger(VaultCardSeed.class);

    // BCrypt cost 10 for CVV — low entropy codes; attempt limiting is the real security guard
    private static final BCryptPasswordEncoder CVV_ENCODER = new BCryptPasswordEncoder(10);

    private static final String SEED_PREFIX = "990000000000";

    /**
     * Build the seed list, encrypting PANs and hashing CVVs.
     * Called once at vault startup.
     */
    static List<VaultCard> build(VaultEncryptor enc) {
        // { pan, expMM, expYY, cvv, holderName, balance, scenario }
        Object[][] seed = {
            // 1. ALWAYS_APPROVED — last4=0010, Luhn-valid
            { SEED_PREFIX + "0010", "12", "27", "111", "Test Approved",      "50000.00", VaultScenario.ALWAYS_APPROVED         },
            // 2. INSUFFICIENT_FUNDS — last4=0028, Luhn-valid
            { SEED_PREFIX + "0028", "12", "27", "222", "Test Insufficient",      "0.00", VaultScenario.INSUFFICIENT_FUNDS      },
            // 3. CARD_EXPIRED — last4=0069, Luhn-valid (expiry 01/20 is in past)
            { SEED_PREFIX + "0069", "01", "20", "333", "Test Expired",        "50000.00", VaultScenario.CARD_EXPIRED            },
            // 4. REQUIRES_OTP_CHALLENGE — last4=0119, Luhn-valid
            { SEED_PREFIX + "0119", "12", "27", "444", "Test OTP Challenge",  "50000.00", VaultScenario.REQUIRES_OTP_CHALLENGE  },
            // 5. PROVIDER_TIMEOUT_BUT_CHARGED — last4=0994, Luhn-valid
            { SEED_PREFIX + "0994", "12", "27", "555", "Test Timeout",        "50000.00", VaultScenario.PROVIDER_TIMEOUT        },
            // 6. DELAYED_CONFIRMATION — last4=1232, Luhn-valid
            { SEED_PREFIX + "1232", "12", "27", "666", "Test Delayed",        "50000.00", VaultScenario.DELAYED_CONFIRMATION    },
            // 7. HARD_DECLINE — last4=2347, Luhn-valid
            { SEED_PREFIX + "2347", "12", "27", "777", "Test Hard Decline",   "50000.00", VaultScenario.HARD_DECLINE            },
            // 8. LATER_REVERSAL — last4=3451, Luhn-valid
            { SEED_PREFIX + "3451", "12", "27", "888", "Test Reversal",       "50000.00", VaultScenario.LATER_REVERSAL          },
        };

        List<VaultCard> cards = new ArrayList<>();
        for (Object[] row : seed) {
            String pan        = (String)        row[0];
            String expMM      = (String)        row[1];
            String expYY      = (String)        row[2];
            String cvv        = (String)        row[3];
            String holder     = (String)        row[4];
            String balance    = (String)        row[5];
            VaultScenario sc  = (VaultScenario) row[6];

            String last4        = pan.substring(pan.length() - 4);
            String panHash      = sha256(pan);
            String encryptedPan = enc.encrypt(pan);
            String cvvHash      = CVV_ENCODER.encode(cvv);

            cards.add(new VaultCard(last4, panHash, encryptedPan, cvvHash, expMM, expYY,
                    holder, new BigDecimal(balance), sc));
        }

        log.info("[SIMVAULT] Seeded {} TEST cards", cards.size());
        return Collections.unmodifiableList(cards);
    }

    static String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
