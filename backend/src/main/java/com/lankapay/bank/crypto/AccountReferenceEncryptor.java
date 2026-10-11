package com.lankapay.bank.crypto;

import com.lankapay.common.exception.LankaPayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM encryption/decryption for the bank account reference stored at rest.
 *
 * Algorithm:  AES/GCM/NoPadding
 * Key length: 256-bit (32-byte Base64-URL-encoded value from env var)
 * IV:         96-bit (12 bytes) randomly generated per encryption
 * Auth tag:   128-bit (16 bytes, GCM default)
 *
 * Ciphertext format (Base64-URL, no padding):
 *   [ 12 bytes IV ][ N bytes ciphertext + 16 bytes auth tag ]
 *
 * Security rules:
 * - The raw key is NEVER logged or included in any response.
 * - A fresh IV is generated for every encrypt() call.
 * - The plaintext account reference is discarded after encryption.
 */
@Component
public class AccountReferenceEncryptor {

    private static final Logger log = LoggerFactory.getLogger(AccountReferenceEncryptor.class);

    private static final String ALGORITHM  = "AES/GCM/NoPadding";
    private static final int    IV_BYTES   = 12;
    private static final int    TAG_BITS   = 128;

    private final SecretKey secretKey;

    public AccountReferenceEncryptor(String base64Key) {
        this(base64Key, "");
    }

    public AccountReferenceEncryptor(
            @Value("${app.encryption.bank-account-key}") String base64Key,
            @Value("${spring.profiles.active:}") String activeProfile) {

        if (activeProfile.contains("prod") && "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=".equals(base64Key.trim())) {
            throw new IllegalStateException(
                    "Default all-zero bank account encryption key is forbidden in production! Set BANK_ACCOUNT_ENCRYPTION_KEY.");
        }
        if ("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=".equals(base64Key.trim())) {
            log.warn("DEVELOPMENT NOTICE: Using default dummy key for bank account encryption. Override with BANK_ACCOUNT_ENCRYPTION_KEY.");
        }

        byte[] keyBytes = Base64.getUrlDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "app.encryption.bank-account-key must decode to exactly 32 bytes (256-bit). "
                    + "Generate with: openssl rand -base64 32");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        log.info("AccountReferenceEncryptor initialised (AES-256-GCM)");
    }

    /**
     * Encrypt a plaintext bank account reference.
     * Returns a Base64-URL encoded string containing IV + ciphertext + auth tag.
     *
     * @param plaintext  The raw account reference (e.g. account number or token)
     * @return           Encrypted, encoded ciphertext safe for DB storage
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = generateIv();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey,
                        new GCMParameterSpec(TAG_BITS, iv));

            byte[] ciphertext = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8));

            // Pack: [iv (12)] + [ciphertext + tag (N+16)]
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ciphertext.length);
            buf.put(iv);
            buf.put(ciphertext);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(buf.array());

        } catch (Exception ex) {
            // Do NOT include plaintext in the exception message
            throw new LankaPayException("ENCRYPTION_ERROR",
                    "Failed to encrypt bank account reference", ex);
        }
    }

    /**
     * Decrypt a previously encrypted bank account reference.
     *
     * @param encoded  Base64-URL encoded ciphertext (from {@link #encrypt})
     * @return         Original plaintext account reference
     */
    public String decrypt(String encoded) {
        try {
            byte[] packed = Base64.getUrlDecoder().decode(encoded);
            ByteBuffer buf = ByteBuffer.wrap(packed);

            byte[] iv = new byte[IV_BYTES];
            buf.get(iv);

            byte[] ciphertext = new byte[buf.remaining()];
            buf.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey,
                        new GCMParameterSpec(TAG_BITS, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);

        } catch (Exception ex) {
            throw new LankaPayException("DECRYPTION_ERROR",
                    "Failed to decrypt bank account reference", ex);
        }
    }

    private byte[] generateIv() {
        byte[] iv = new byte[IV_BYTES];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
}
