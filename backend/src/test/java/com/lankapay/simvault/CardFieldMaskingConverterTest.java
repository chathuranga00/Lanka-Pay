package com.lankapay.simvault;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.lankapay.simvault.logging.CardFieldMaskingConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CardFieldMaskingConverter.
 * Verifies that PAN/CVV/cardNumber/expiry field values are redacted.
 */
@DisplayName("CardFieldMaskingConverter — log masking")
class CardFieldMaskingConverterTest {

    CardFieldMaskingConverter converter;
    LoggerContext ctx;

    @BeforeEach
    void setUp() {
        ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        converter = new CardFieldMaskingConverter();
        converter.start();
    }

    private String convert(String msg) {
        Logger logger = ctx.getLogger("test");
        LoggingEvent event = new LoggingEvent(
                "com.lankapay.simvault", logger, Level.INFO, msg, null, null);
        return converter.convert(event);
    }

    private static final String PAN_PREFIX = "990000000000";

    @Test
    @DisplayName("redacts JSON pan field")
    void redactsPanField() {
        String pan = PAN_PREFIX + "0010";
        String result = convert("{\"pan\":\"" + pan + "\"}");
        assertThat(result).contains("[REDACTED-PAN]");
        assertThat(result).doesNotContain(pan);
    }

    @Test
    @DisplayName("redacts JSON cvv field")
    void redactsCvvField() {
        String result = convert("{\"cvv\":\"100\"}");
        assertThat(result).contains("[REDACTED-CVV]");
        assertThat(result).doesNotContain("\"100\"");
    }

    @Test
    @DisplayName("redacts cardNumber field")
    void redactsCardNumberField() {
        String pan = PAN_PREFIX + "0010";
        String result = convert("{\"cardNumber\":\"" + pan + "\"}");
        assertThat(result).contains("[REDACTED-PAN]");
        assertThat(result).doesNotContain(pan);
    }

    @Test
    @DisplayName("redacts expiry field")
    void redactsExpiryField() {
        String result = convert("{\"expiry\":\"12/27\"}");
        assertThat(result).contains("[REDACTED-EXPIRY]");
        assertThat(result).doesNotContain("12/27");
    }

    @Test
    @DisplayName("redacts raw 99xxxxxxxxxxxxxx PAN-like digit run")
    void redactsRawTestPan() {
        String pan = PAN_PREFIX + "0010";
        String result = convert("Processing card " + pan + " for user 1");
        assertThat(result).contains("[REDACTED-PAN]");
        assertThat(result).doesNotContain(pan);
    }

    @Test
    @DisplayName("non-card log messages pass through unchanged")
    void nonCardLogsPassThrough() {
        String msg = "Transfer completed ref=abc-123 amount=1500.00 LKR";
        assertThat(convert(msg)).isEqualTo(msg);
    }

    @Test
    @DisplayName("case-insensitive field matching")
    void caseInsensitive() {
        String pan = PAN_PREFIX + "0011";
        assertThat(convert("{\"PAN\":\"" + pan + "\"}")).contains("[REDACTED-PAN]");
        assertThat(convert("{\"CVV\":\"100\"}")).contains("[REDACTED-CVV]");
    }
}
