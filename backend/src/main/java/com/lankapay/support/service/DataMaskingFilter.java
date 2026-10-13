package com.lankapay.support.service;

import java.util.regex.Pattern;

/**
 * Sensitive data masking filter applied before persisting messages.
 * Automatically redacts:
 * 1. 13-19 digit account / credit / debit card PAN numbers (with or without spaces/dashes).
 * 2. 6-digit verification codes, PINs, or OTPs.
 */
public final class DataMaskingFilter {

    private DataMaskingFilter() {}

    // 13-19 digits, allowing optional spaces or hyphens between groups of digits
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile(
            "(?<!\\d)(?:\\d[ -]?){12,18}\\d(?!\\d)"
    );

    // Exact 6-digit codes (OTP/PIN)
    private static final Pattern SIX_DIGIT_OTP_PATTERN = Pattern.compile(
            "(?<!\\d)\\d{6}(?!\\d)"
    );

    // HTML tags to sanitize
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>", Pattern.CASE_INSENSITIVE);

    public static String mask(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        // Mask 13-19 digit card numbers first
        String masked = CARD_NUMBER_PATTERN.matcher(input).replaceAll("[CARD_NUMBER_MASKED]");

        // Mask 6-digit OTP codes next
        masked = SIX_DIGIT_OTP_PATTERN.matcher(masked).replaceAll("[OTP_MASKED]");

        // Sanitize raw HTML tags to prevent XSS / raw HTML injection in chats
        masked = HTML_TAG_PATTERN.matcher(masked).replaceAll("");
        masked = masked.replace("<", "&lt;").replace(">", "&gt;");

        return masked;
    }
}
