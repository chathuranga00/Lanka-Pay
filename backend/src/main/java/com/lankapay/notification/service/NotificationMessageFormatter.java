package com.lankapay.notification.service;

import com.lankapay.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Formats notifications for Section 12 events and strictly enforces lock-screen privacy:
 * - NO amounts (no currency keywords e.g. LKR, Rs, $, nor numeric decimal amounts e.g. 1500.00).
 * - NO account digits (no account numbers, no masked digits like ***1234 or sequences of 4+ digits).
 */
@Component
public class NotificationMessageFormatter {

    // Regex detecting currency symbols, currency codes, or decimal amount patterns
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?i)(\\bLKR\\b|\\bRs\\.?|\\$|€|£|\\b\\d+\\.\\d{2}\\b)"
    );

    // Regex detecting 4 or more consecutive digits, or asterisks followed by digits
    private static final Pattern ACCOUNT_DIGITS_PATTERN = Pattern.compile(
            "(\\b\\d{4,}\\b|\\*{2,}\\d+)"
    );

    public record FormattedMessage(String title, String message) {}

    public FormattedMessage formatPaymentSent(String recipientName) {
        String cleanName = recipientName != null && !recipientName.isBlank() ? recipientName.trim() : "recipient";
        String separator = cleanName.endsWith(".") ? " " : ". ";
        String message = "Payment sent to " + cleanName + separator + "Tap to view receipt.";
        return create("Payment Sent", message);
    }

    public FormattedMessage formatPaymentReceived(String senderName) {
        String cleanName = senderName != null && !senderName.isBlank() ? senderName.trim() : "sender";
        String separator = cleanName.endsWith(".") ? " " : ". ";
        String message = "You received a payment from " + cleanName + separator + "Open app to view details.";
        return create("Payment Received", message);
    }

    public FormattedMessage formatPaymentFailed() {
        return create("Payment Failed", "Your transfer could not be completed. Tap to view details.");
    }

    public FormattedMessage formatPaymentReversed() {
        return create("Payment Reversed", "A payment reversal has been processed. Open app to view details.");
    }

    public FormattedMessage formatAccountLinked() {
        return create("Bank Account Linked", "A new bank account was linked to your profile.");
    }

    public FormattedMessage formatAccountRemoved() {
        return create("Bank Account Removed", "A bank account was removed from your profile.");
    }

    public FormattedMessage formatSecurityAlert(String alertReason) {
        String detail = alertReason != null && !alertReason.isBlank() ? alertReason : "Your security settings were updated.";
        // Clean out any sensitive digits if present in reason
        String sanitized = sanitize(detail);
        return create("Security Alert", sanitized + " If this was not you, contact support immediately.");
    }

    public FormattedMessage formatPaymentRequestReceived(String requesterName) {
        String cleanName = requesterName != null && !requesterName.isBlank() ? requesterName : "a user";
        return create("Payment Request Received", "You received a payment request from " + cleanName + ". Open app to review.");
    }

    public FormattedMessage formatGeneral(String title, String message) {
        return create(title != null ? title : "Account Notice", message);
    }

    private FormattedMessage create(String title, String rawMessage) {
        String sanitizedMessage = sanitize(rawMessage);
        assertPrivacySafe(sanitizedMessage);
        return new FormattedMessage(title, sanitizedMessage);
    }

    /**
     * Asserts that the notification text contains NO amounts and NO account digits.
     * Throws IllegalStateException if violated.
     */
    public static void assertPrivacySafe(String text) {
        if (text == null) return;

        if (AMOUNT_PATTERN.matcher(text).find()) {
            throw new IllegalStateException("Privacy violation: Notification contains monetary amount: " + text);
        }

        if (ACCOUNT_DIGITS_PATTERN.matcher(text).find()) {
            throw new IllegalStateException("Privacy violation: Notification contains account digits: " + text);
        }
    }

    /**
     * Sanitizes a string by stripping any accidental amounts or account digits.
     */
    public static String sanitize(String text) {
        if (text == null) return "";
        // Strip amounts like "1500.00 LKR" or "LKR 500.00"
        String cleaned = text.replaceAll("(?i)\\b\\d+\\.\\d{2}\\s*(LKR|Rs\\.?)?", "[amount]")
                             .replaceAll("(?i)(LKR|Rs\\.?|\\$|€|£)\\s*\\d+(\\.\\d{2})?", "[amount]");
        // Strip 4+ digits
        cleaned = cleaned.replaceAll("\\b\\d{4,}\\b", "[account]")
                         .replaceAll("\\*{2,}\\d+", "[account]");
        return cleaned;
    }
}
