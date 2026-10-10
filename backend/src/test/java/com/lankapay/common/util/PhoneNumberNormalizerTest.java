package com.lankapay.common.util;

import com.lankapay.common.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for PhoneNumberNormalizer.
 * No Spring context required.
 */
class PhoneNumberNormalizerTest {

    // ── Valid formats → E.164 ──────────────────────────────────

    @Nested @DisplayName("normalize() – valid inputs")
    class ValidInputs {

        @ParameterizedTest(name = "[{index}] \"{0}\" → \"{1}\"")
        @CsvSource({
            // Local 10-digit with leading zero
            "+94771234567,  +94771234567",
            "0771234567,    +94771234567",
            "077-123-4567,  +94771234567",
            "077 123 4567,  +94771234567",
            // Local 9-digit (no leading zero)
            "771234567,     +94771234567",
            // International with + prefix
            "+94701234567,  +94701234567",
            "+94751234567,  +94751234567",
            "+94761234567,  +94761234567",
            "+94781234567,  +94781234567",
            // 00-prefixed international
            "0094771234567, +94771234567",
            // Whitespace/hyphens everywhere
            "0 77-1234 567, +94771234567",
        })
        void normalises(String input, String expected) {
            assertThat(PhoneNumberNormalizer.normalize(input.trim()))
                    .isEqualTo(expected.trim());
        }

        @Test
        @DisplayName("all valid SL mobile prefixes are accepted")
        void validPrefixes() {
            String[] prefixes = {"70", "71", "72", "74", "75", "76", "77", "78"};
            for (String prefix : prefixes) {
                String input = "+94" + prefix + "1234567";
                assertThatCode(() -> PhoneNumberNormalizer.normalize(input))
                        .as("prefix %s should be accepted", prefix)
                        .doesNotThrowAnyException();
            }
        }
    }

    // ── Invalid formats → ValidationException ─────────────────

    @Nested @DisplayName("normalize() – invalid inputs")
    class InvalidInputs {

        @Test
        @DisplayName("null → PHONE_REQUIRED")
        void nullInput() {
            assertThatThrownBy(() -> PhoneNumberNormalizer.normalize(null))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo("PHONE_REQUIRED");
        }

        @Test
        @DisplayName("blank → PHONE_REQUIRED")
        void blankInput() {
            assertThatThrownBy(() -> PhoneNumberNormalizer.normalize("   "))
                    .isInstanceOf(ValidationException.class)
                    .extracting("errorCode").isEqualTo("PHONE_REQUIRED");
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" is invalid")
        @ValueSource(strings = {
            // Landline prefix (not mobile)
            "0112345678",
            "+94112345678",
            // Too short
            "077123",
            "+9477123",
            // Too long
            "07712345678",
            "+947712345678",
            // Invalid prefix 73
            "+94731234567",
            "0731234567",
            // Invalid prefix 79
            "+94791234567",
            // Non-numeric
            "+94771234ABC",
            "ABCDEFGHIJ",
            // Empty
            "",
        })
        void rejectsInvalidFormat(String input) {
            assertThatThrownBy(() -> PhoneNumberNormalizer.normalize(input))
                    .isInstanceOf(ValidationException.class);
        }
    }

    // ── mask() ─────────────────────────────────────────────────

    @Nested @DisplayName("mask()")
    class Mask {

        @Test
        @DisplayName("masks middle digits correctly")
        void masksCorrectly() {
            assertThat(PhoneNumberNormalizer.mask("+94771234567"))
                    .isEqualTo("+9477***4567");
        }

        @Test
        @DisplayName("handles short/null strings gracefully")
        void shortString() {
            assertThat(PhoneNumberNormalizer.mask(null)).isEqualTo("***");
            assertThat(PhoneNumberNormalizer.mask("123")).isEqualTo("***");
        }
    }
}
