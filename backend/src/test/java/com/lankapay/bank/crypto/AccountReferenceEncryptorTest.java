package com.lankapay.bank.crypto;

import com.lankapay.common.exception.LankaPayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for AccountReferenceEncryptor (AES-256-GCM).
 * No Spring context required.
 */
class AccountReferenceEncryptorTest {

    // 32 zero bytes, Base64URL encoded — valid 256-bit test key
    private static final String TEST_KEY =
            Base64.getUrlEncoder().withoutPadding()
                  .encodeToString(new byte[32]);

    AccountReferenceEncryptor encryptor;

    @BeforeEach
    void setUp() {
        encryptor = new AccountReferenceEncryptor(TEST_KEY);
    }

    @Test
    @DisplayName("encrypt → decrypt round-trip returns original plaintext")
    void roundTrip() {
        String plaintext = "101001";
        String encrypted = encryptor.encrypt(plaintext);
        assertThat(encryptor.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("encrypt produces different ciphertext each call (fresh IV)")
    void freshIvEachCall() {
        String a = encryptor.encrypt("101001");
        String b = encryptor.encrypt("101001");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("ciphertext does not contain plaintext")
    void ciphertextOpaqueToPlaintext() {
        String encrypted = encryptor.encrypt("101001");
        assertThat(encrypted).doesNotContain("101001");
    }

    @Test
    @DisplayName("decrypt throws on tampered ciphertext")
    void tamperedCiphertextThrows() {
        String encrypted = encryptor.encrypt("101001");
        // Flip the last character to corrupt the auth tag
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "XX";
        assertThatThrownBy(() -> encryptor.decrypt(tampered))
                .isInstanceOf(LankaPayException.class)
                .extracting("errorCode").isEqualTo("DECRYPTION_ERROR");
    }

    @Test
    @DisplayName("constructor rejects key shorter than 32 bytes")
    void shortKeyRejected() {
        String shortKey = Base64.getUrlEncoder().withoutPadding()
                                .encodeToString(new byte[16]);
        assertThatThrownBy(() -> new AccountReferenceEncryptor(shortKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("encrypt handles long account references")
    void longReference() {
        String longRef = "A".repeat(200);
        assertThat(encryptor.decrypt(encryptor.encrypt(longRef))).isEqualTo(longRef);
    }
}
