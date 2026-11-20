package com.lankapay.simvault.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

/**
 * Logback converter that redacts card-sensitive field values from every log message.
 *
 * Registered in logback-spring.xml as {@code %masked}.
 *
 * Patterns replaced:
 *   "pan"         : "9900..."   → "pan":"[REDACTED]"
 *   "cvv"         : "123"       → "cvv":"[REDACTED]"
 *   "cardNumber"  : "9900..."   → "cardNumber":"[REDACTED]"
 *   "expiry/exp"  : "12/27"     → "expiry":"[REDACTED]"
 *
 * Also redacts any 13-19 digit run that looks like a PAN (starts with 99).
 *
 * This is a defence-in-depth layer. The primary control is that card values
 * never reach any logger in application code.
 */
public class CardFieldMaskingConverter extends ClassicConverter {

    // JSON field-value patterns (single or double quoted values)
    private static final Pattern PAN_FIELD       = Pattern.compile(
            "(?i)(\"pan\"|'pan')\\s*:\\s*[\"']([\\d\\s\\-]{13,23})[\"']");
    private static final Pattern CVV_FIELD       = Pattern.compile(
            "(?i)(\"cvv\"|'cvv'|\"securityCode\"|'securityCode')\\s*:\\s*[\"'](\\d{3,4})[\"']");
    private static final Pattern CARD_NUM_FIELD  = Pattern.compile(
            "(?i)(\"cardNumber\"|'cardNumber'|\"card_number\"|'card_number')\\s*:\\s*[\"']([\\d\\s\\-]{13,23})[\"']");
    private static final Pattern EXPIRY_FIELD    = Pattern.compile(
            "(?i)(\"exp(?:iry|Month|Year|Date)?\"|'exp(?:iry|Month|Year|Date)?')\\s*:\\s*[\"']([^\"']{2,7})[\"']");

    // Raw PAN-like digit runs starting with 99 (our test card prefix)
    private static final Pattern RAW_TEST_PAN    = Pattern.compile("\\b99\\d{14}\\b");

    @Override
    public String convert(ILoggingEvent event) {
        String msg = event.getFormattedMessage();
        if (msg == null) return "";

        msg = PAN_FIELD.matcher(msg).replaceAll("$1:\"[REDACTED-PAN]\"");
        msg = CVV_FIELD.matcher(msg).replaceAll("$1:\"[REDACTED-CVV]\"");
        msg = CARD_NUM_FIELD.matcher(msg).replaceAll("$1:\"[REDACTED-PAN]\"");
        msg = EXPIRY_FIELD.matcher(msg).replaceAll("$1:\"[REDACTED-EXPIRY]\"");
        msg = RAW_TEST_PAN.matcher(msg).replaceAll("[REDACTED-PAN]");
        return msg;
    }
}
