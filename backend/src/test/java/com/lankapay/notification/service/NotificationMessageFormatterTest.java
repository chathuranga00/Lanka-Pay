package com.lankapay.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationMessageFormatterTest {

    NotificationMessageFormatter formatter = new NotificationMessageFormatter();

    @Test
    @DisplayName("Lock-screen privacy: formatPaymentSent contains no amounts or account digits")
    void testFormatPaymentSentIsPrivacySafe() {
        var msg = formatter.formatPaymentSent("Kamal P.");

        assertThat(msg.title()).isEqualTo("Payment Sent");
        assertThat(msg.message()).isEqualTo("Payment sent to Kamal P. Tap to view receipt.");
        assertThat(msg.message()).doesNotContain("LKR", "1500", "5000", ".00");
        assertThat(msg.message()).doesNotMatch(".*\\d{4,}.*");

        // Passes privacy assertion
        NotificationMessageFormatter.assertPrivacySafe(msg.message());
    }

    @Test
    @DisplayName("Lock-screen privacy: formatPaymentReceived contains no amounts or account digits")
    void testFormatPaymentReceivedIsPrivacySafe() {
        var msg = formatter.formatPaymentReceived("Nimal S.");

        assertThat(msg.title()).isEqualTo("Payment Received");
        assertThat(msg.message()).isEqualTo("You received a payment from Nimal S. Open app to view details.");
        assertThat(msg.message()).doesNotContain("LKR", "1500", ".00");
        assertThat(msg.message()).doesNotMatch(".*\\d{4,}.*");

        NotificationMessageFormatter.assertPrivacySafe(msg.message());
    }

    @Test
    @DisplayName("Lock-screen privacy: other events are strictly privacy-safe")
    void testOtherEventsArePrivacySafe() {
        var failed = formatter.formatPaymentFailed();
        NotificationMessageFormatter.assertPrivacySafe(failed.message());

        var reversed = formatter.formatPaymentReversed();
        NotificationMessageFormatter.assertPrivacySafe(reversed.message());

        var linked = formatter.formatAccountLinked();
        NotificationMessageFormatter.assertPrivacySafe(linked.message());

        var removed = formatter.formatAccountRemoved();
        NotificationMessageFormatter.assertPrivacySafe(removed.message());

        var security = formatter.formatSecurityAlert("Your PIN was updated.");
        NotificationMessageFormatter.assertPrivacySafe(security.message());

        var pmtReq = formatter.formatPaymentRequestReceived("Supermarket");
        NotificationMessageFormatter.assertPrivacySafe(pmtReq.message());
    }

    @Test
    @DisplayName("assertPrivacySafe rejects strings with monetary amounts")
    void testAssertPrivacySafeRejectsAmounts() {
        assertThatThrownBy(() -> NotificationMessageFormatter.assertPrivacySafe("You sent LKR 2500.00 to Kamal"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contains monetary amount");

        assertThatThrownBy(() -> NotificationMessageFormatter.assertPrivacySafe("Payment of 500.00 processed"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contains monetary amount");

        assertThatThrownBy(() -> NotificationMessageFormatter.assertPrivacySafe("Paid Rs. 100"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contains monetary amount");
    }

    @Test
    @DisplayName("assertPrivacySafe rejects strings with account digits or masked account numbers")
    void testAssertPrivacySafeRejectsAccountDigits() {
        assertThatThrownBy(() -> NotificationMessageFormatter.assertPrivacySafe("Debited from account 123456789"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contains account digits");

        assertThatThrownBy(() -> NotificationMessageFormatter.assertPrivacySafe("Debited from account ****4567"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contains account digits");
    }
}
