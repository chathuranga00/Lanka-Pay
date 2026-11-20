package com.lankapay.support.service;

import com.lankapay.support.domain.FaqItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class FaqBotEngine {

    public static final String SAFETY_WARNING =
            "Security Notice: LankaPay agents and automated assistants will NEVER ask for your password, PIN, OTP, or card number. Never share sensitive credentials.";

    private final List<FaqItem> faqTable = new ArrayList<>();

    public FaqBotEngine() {
        seedFaqs();
    }

    private void seedFaqs() {
        faqTable.add(new FaqItem(
                "faq_lookup",
                "How lookup works",
                "How does recipient lookup work?",
                List.of("lookup", "proxy", "phone", "nic", "recipient", "registry", "resolve", "find user"),
                "LankaPay ID uses a central proxy registry to link your phone number or NIC to your primary bank account. " +
                        "When sending money, entering a phone number resolves the recipient's verified account holder name " +
                        "without exposing their bank account number."
        ));

        faqTable.add(new FaqItem(
                "faq_pending",
                "Why a payment is pending",
                "Why is my payment pending or uncertain?",
                List.of("pending", "uncertain", "delay", "processing", "timeout", "hold", "stuck", "status"),
                "Payments show as PENDING or UNCERTAIN when the destination bank has not yet returned network confirmation. " +
                        "Automated reconciliation runs every 30 seconds to confirm the final state. " +
                        "If not settled within 5 minutes, funds remain safe or automatically reverse."
        ));

        faqTable.add(new FaqItem(
                "faq_freeze_card",
                "How to freeze a card",
                "How do I freeze or unfreeze my card?",
                List.of("freeze", "unfreeze", "block", "lost", "stolen", "card", "lock card"),
                "You can instantly freeze or unfreeze your card anytime from the Cards section on the Home or Explore tab. " +
                        "Select your card and toggle 'Freeze Card'. This blocks all POS and e-commerce transactions immediately."
        ));

        faqTable.add(new FaqItem(
                "faq_link_account",
                "How to link an account",
                "How do I link a new bank account?",
                List.of("link", "account", "add bank", "connect bank", "mandate", "bank account", "add account"),
                "To link an account, navigate to Accounts > Link Bank Account, select your participating LankaPay member bank, " +
                        "and authenticate via your bank's secure e-mandate or OTP flow. Once verified, it becomes available for instant transfers."
        ));
    }

    public List<FaqItem> getAllFaqs() {
        return Collections.unmodifiableList(faqTable);
    }

    public Optional<FaqItem> findById(String id) {
        return faqTable.stream().filter(f -> f.id().equalsIgnoreCase(id)).findFirst();
    }

    public boolean isAgentRequest(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("agent")
                || lower.contains("human")
                || lower.contains("representative")
                || lower.contains("operator")
                || lower.contains("support person")
                || lower.contains("talk to someone");
    }

    public record BotAnswer(String text, boolean transferToAgent, String matchedFaqId) {}

    public BotAnswer answerUserMessage(String userText) {
        if (userText == null || userText.isBlank()) {
            return new BotAnswer("How can I assist you today? You can select a quick FAQ or ask a question.", false, null);
        }

        if (isAgentRequest(userText)) {
            return new BotAnswer(
                    "I am connecting you with a support agent. An agent will review your inquiry and reply shortly.\n\n" +
                            "Reminder: LankaPay agents will NEVER ask for your password, PIN, OTP, or card number.",
                    true,
                    null
            );
        }

        // Check if user clicked an exact FAQ ID
        Optional<FaqItem> directFaq = findById(userText.trim());
        if (directFaq.isPresent()) {
            FaqItem faq = directFaq.get();
            return new BotAnswer(
                    faq.answer() + "\n\nDid this help? If you need personal assistance, reply 'Talk to an agent'.",
                    false,
                    faq.id()
            );
        }

        // Keyword matching
        String lower = userText.toLowerCase();
        for (FaqItem faq : faqTable) {
            for (String kw : faq.keywords()) {
                if (lower.contains(kw)) {
                    return new BotAnswer(
                            faq.answer() + "\n\nDid this help? If you need personal assistance, reply 'Talk to an agent'.",
                            false,
                            faq.id()
                    );
                }
            }
        }

        // Fallback
        return new BotAnswer(
                "I couldn't find a direct match for your question in our FAQ. Would you like to speak with a human support agent? " +
                        "Reply 'Talk to an agent' or attach your transaction reference.\n\n" +
                        SAFETY_WARNING,
                false,
                null
        );
    }
}
