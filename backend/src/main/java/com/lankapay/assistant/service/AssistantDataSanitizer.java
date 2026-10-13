package com.lankapay.assistant.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Privacy Sanitizer for AI Assistant prompts.
 * Enforces strict PII, card, account, phone number, and exact amount stripping before prompt construction.
 * Invariant: No PII, exact monetary amounts, secrets, or support chat messages may reach any LLM provider.
 */
@Component
public class AssistantDataSanitizer {

    // 13 to 19 digit PANs (with optional hyphens or spaces)
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile(
            "\\b(?:\\d[ -]*?){13,19}\\b"
    );

    // 6-digit OTPs / PINs
    private static final Pattern OTP_PATTERN = Pattern.compile(
            "\\b\\d{6}\\b"
    );

    // Sri Lankan & International phone numbers (+94..., 07..., etc.)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?:\\+94|0094|0)[0-9]{8,10}\\b|(?:\\+94|0094|0)[ -]?[0-9]{2}[ -]?[0-9]{3}[ -]?[0-9]{4}\\b"
    );

    // Bank Account numbers (e.g. 10 to 12 consecutive digits or LK... IBANs)
    private static final Pattern IBAN_ACCOUNT_PATTERN = Pattern.compile(
            "\\b(?:LK\\d{2}[A-Za-z]{4}\\d{16}|[0-9]{10,12})\\b",
            Pattern.CASE_INSENSITIVE
    );

    // References and UUIDs
    private static final Pattern REF_UUID_PATTERN = Pattern.compile(
            "\\b(?:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|(?:TXN|REF|TRX|INV)-[A-Za-z0-9-]+)\\b"
    );

    // Exact monetary currency amounts (e.g. LKR 15,200.50, Rs. 5000, $250.00, 4500 LKR)
    private static final Pattern EXACT_AMOUNT_PATTERN = Pattern.compile(
            "(?:(?:LKR|Rs\\.?|USD|\\$)\\s*[0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?\\s*(?:LKR|Rs\\.?|rupees?))",
            Pattern.CASE_INSENSITIVE
    );

    // Secrets / credentials
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)\\b(?:password|pin|otp|secret|apiKey|api_key|token)\\s*[:=]\\s*\\S+"
    );

    /**
     * Sanitize user input by stripping cards, OTPs, phones, accounts, refs, secrets, and exact amounts.
     */
    public String sanitizeUserPrompt(String rawPrompt) {
        if (rawPrompt == null || rawPrompt.isBlank()) {
            return "";
        }

        String result = rawPrompt;

        // 1. Secrets
        result = SECRET_PATTERN.matcher(result).replaceAll("[SECRET_MASKED]");

        // 2. Card numbers (13-19 digits)
        result = CARD_NUMBER_PATTERN.matcher(result).replaceAll("[CARD_MASKED]");

        // 3. Phone numbers (before account numbers, so +94... is not consumed as 10-11 digit accounts)
        result = PHONE_PATTERN.matcher(result).replaceAll("[PHONE_MASKED]");

        // 4. IBAN / Account numbers
        result = IBAN_ACCOUNT_PATTERN.matcher(result).replaceAll("[ACCOUNT_MASKED]");

        // 5. 6-digit OTPs
        result = OTP_PATTERN.matcher(result).replaceAll("[OTP_MASKED]");

        // 6. UUIDs and transaction references
        result = REF_UUID_PATTERN.matcher(result).replaceAll("[REF_MASKED]");

        // 7. Exact monetary amounts -> coarse [AMOUNT]
        result = EXACT_AMOUNT_PATTERN.matcher(result).replaceAll("[AMOUNT]");

        return result.trim();
    }
}
