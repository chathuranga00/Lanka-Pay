package com.lankapay.simvault.internal;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption used exclusively inside the vault.
 *
 * ISOLATION: package-private — no other module may import this class.
 *
 * Identical algorithm to AccountReferenceEncryptor but uses a SEPARATE key
 * (VAULT_ENCRYPTION_KEY) so a leaked bank-account key cannot decrypt card data.
 */
final class VaultEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES     = 12;
    private static final int TAG_BITS     = 128;

    private final SecretKey key;

    VaultEncryptor(String base64Key) {
        byte[] keyBytes = Base64.getUrlDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "VAULT_ENCRYPTION_KEY must decode to exactly 32 bytes (256-bit). "
                    + "Generate: openssl rand -base64 32 | tr '+/' '-_' | tr -d '='");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** Encrypt plaintext. Returns Base64-URL(IV ‖ ciphertext ‖ tag). */
    String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(IV_BYTES + ct.length);
            buf.put(iv); buf.put(ct);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(buf.array());
        } catch (Exception ex) {
            // Never include plaintext in the message
            throw new RuntimeException("VaultEncryptor.encrypt failed", ex);
        }
    }

    /** Decrypt a value produced by {@link #encrypt}. */
    String decrypt(String encoded) {
        try {
            byte[] packed = Base64.getUrlDecoder().decode(encoded);
            ByteBuffer buf = ByteBuffer.wrap(packed);
            byte[] iv = new byte[IV_BYTES]; buf.get(iv);
            byte[] ct = new byte[buf.remaining()]; buf.get(ct);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new RuntimeException("VaultEncryptor.decrypt failed", ex);
        }
    }
}
