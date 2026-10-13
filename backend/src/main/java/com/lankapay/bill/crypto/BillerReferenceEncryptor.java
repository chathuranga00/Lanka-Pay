package com.lankapay.bill.crypto;

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
 * AES-256-GCM encryption/decryption for biller customer references stored at rest.
 *
 * Requirements:
 * - Customer reference is stored encrypted in database/store.
 * - Responses only return the masked customer reference.
 */
@Component
public class BillerReferenceEncryptor {

    private static final Logger log = LoggerFactory.getLogger(BillerReferenceEncryptor.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey secretKey;

    public BillerReferenceEncryptor(
            @Value("${app.encryption.bank-account-key}") String base64Key) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getUrlDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        }
        if (keyBytes.length != 32) {
            // Pad or trim if in test/dev
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            keyBytes = padded;
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypt customer reference to Base64-URL string containing IV + ciphertext + tag.
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new LankaPayException("ENCRYPTION_ERROR", "Failed to encrypt customer reference: " + e.getMessage());
        }
    }

    /**
     * Decrypt customer reference ciphertext back to plaintext.
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) return null;
        try {
            byte[] combined;
            try {
                combined = Base64.getUrlDecoder().decode(ciphertext.trim());
            } catch (IllegalArgumentException e) {
                combined = Base64.getDecoder().decode(ciphertext.trim());
            }

            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt customer reference: {}", e.getMessage());
            throw new LankaPayException("DECRYPTION_ERROR", "Failed to decrypt customer reference");
        }
    }

    /**
     * Masks customer reference for display/responses (e.g. "0771234567" -> "077****567" or "1002345678" -> "****5678").
     */
    public static String mask(String ref) {
        if (ref == null || ref.isBlank()) return "";
        String s = ref.trim();
        if (s.length() <= 4) {
            return "****" + s;
        }
        if (s.length() >= 10 && s.startsWith("07")) {
            // Phone pattern: 077****456
            return s.substring(0, 3) + "****" + s.substring(s.length() - 3);
        }
        // General pattern: last 4 digits visible
        int maskLen = Math.max(4, s.length() - 4);
        return "*".repeat(maskLen) + s.substring(s.length() - 4);
    }
}
