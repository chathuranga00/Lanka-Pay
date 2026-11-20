package com.lankapay.assistant.client;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Default rule-grounded LLM Client for LankaPay AI Assistant.
 * Answers app capability questions and summarizes user aggregated insights.
 * Strictly adheres to safety guardrails: refuses money movements, setting changes, and financial/legal advice.
 * Invariant: Never logs prompt content or completion responses.
 */
@Component
@Primary
public class MockLlmClient implements LlmClient {

    @Override
    public String complete(String systemPrompt, String sanitizedUserPrompt) {
        if (sanitizedUserPrompt == null || sanitizedUserPrompt.isBlank()) {
            return "How can I help you today? You can ask me how app features work or ask for a summary of your spending insights.";
        }

        String lower = sanitizedUserPrompt.toLowerCase(Locale.ROOT);

        // Guardrail 1: Refusal of money movement
        if (lower.contains("send money to") || lower.contains("pay my") || lower.contains("transfer to")
                || lower.contains("withdraw") || lower.contains("deposit money")
                || (lower.contains("send") && lower.contains("[amount]"))
                || (lower.contains("pay") && lower.contains("[amount]"))) {
            return "I am an AI assistant and cannot move money, make transfers, or process payments on your behalf. "
                    + "Please use the 'Send Money', 'Pay Bills', or 'QR' tabs to complete transactions securely.";
        }

        // Guardrail 2: Refusal of account settings / credentials changes
        if (lower.contains("change my pin") || lower.contains("reset password") || lower.contains("freeze my card")
                || lower.contains("change settings") || lower.contains("update phone")) {
            return "I cannot change account settings or security credentials. "
                    + "Please manage your security settings directly in the Settings or Cards screen.";
        }

        // Guardrail 3: Refusal of investment, tax, or legal advice
        if (lower.contains("invest") || lower.contains("stock") || lower.contains("crypto")
                || lower.contains("tax advice") || lower.contains("legal advice") || lower.contains("financial advice")) {
            return "I cannot provide investment, tax, or legal financial advice. "
                    + "I can only provide factual summaries of app features and your aggregated account insights.";
        }

        // Feature Explanation: Insights Summary
        if (lower.contains("insight") || lower.contains("spending summary") || lower.contains("overview")
                || lower.contains("how much did i spend") || lower.contains("budget")) {
            if (systemPrompt != null && systemPrompt.contains("USER_INSIGHTS:")) {
                String insightsPart = systemPrompt.substring(systemPrompt.indexOf("USER_INSIGHTS:"));
                return "Here is a summary of your aggregated insights:\n"
                        + insightsPart.replace("USER_INSIGHTS:", "").trim();
            }
            return "You can view your complete spending analysis and category breakdowns anytime in the Analytics tab.";
        }

        // Feature Explanation: Transfers & Send Money
        if (lower.contains("how to send") || lower.contains("send money") || lower.contains("transfer")) {
            return "To send money, tap 'Send' on the home screen or navigation bar. "
                    + "You can transfer funds instantly using LankaPay ID, bank account number, or mobile number.";
        }

        // Feature Explanation: QR Payments
        if (lower.contains("qr") || lower.contains("lankaqr") || lower.contains("scan")) {
            return "LankaPay supports standard LANKAQR merchant codes. "
                    + "Tap 'Scan QR' on the home screen, point your camera at the merchant QR code, and confirm the payment.";
        }

        // Feature Explanation: Bill Payments & Scheduling
        if (lower.contains("bill") || lower.contains("schedule")) {
            return "You can pay utility, telco, and insurance bills under 'Pay Bills'. "
                    + "You can also schedule recurring payments with automatic execution and payment reminders.";
        }

        // Feature Explanation: Bill Splitting
        if (lower.contains("split")) {
            return "The 'Split Bill' feature allows you to divide expenses evenly or by custom amounts with contacts, "
                    + "generating payment requests directly in the app.";
        }

        // Feature Explanation: Cards & Tokenization
        if (lower.contains("card") || lower.contains("token")) {
            return "You can link debit and credit cards in the Cards tab. "
                    + "All card details are safely tokenized using LankaPay Simulated Vault for secure contactless and in-app transactions.";
        }

        // Feature Explanation: Rewards
        if (lower.contains("reward") || lower.contains("point")) {
            return "Earn LankaPay reward points automatically on eligible transfers, bill payments, and QR purchases. "
                    + "Redeem points for merchant vouchers in the Rewards tab.";
        }

        // General fallback
        return "I can explain LankaPay features (such as sending money, QR payments, bill payments, and cards) "
                + "or summarize your high-level spending insights. What would you like to know?";
    }
}
