import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../domain/chat_models.dart';

// ── Curated FAQs ─────────────────────────────────────────────────────────────

const List<FaqShortcut> curatedFaqs = [
  FaqShortcut(
    id: 'faq_lookup',
    title: 'How lookup works',
    question: 'How does recipient lookup work?',
    answer: 'LankaPay ID uses a central proxy registry to link your phone number or NIC to your bank account. '
        'Entering a phone number securely resolves the recipient verified name without revealing their account number.',
  ),
  FaqShortcut(
    id: 'faq_pending',
    title: 'Why a payment is pending',
    question: 'Why is my payment pending or uncertain?',
    answer: 'Payments show as PENDING when the clearing network has not returned instant settlement confirmation. '
        'Automated reconciliation reconciles the transaction within minutes or reverses funds safely.',
  ),
  FaqShortcut(
    id: 'faq_freeze_card',
    title: 'How to freeze a card',
    question: 'How do I freeze or unfreeze my card?',
    answer: 'You can instantly freeze or unfreeze your card anytime from the Cards section on the Home or Explore tab. '
        'Toggling Freeze Card stops all POS and online transactions immediately.',
  ),
  FaqShortcut(
    id: 'faq_link_account',
    title: 'How to link an account',
    question: 'How do I link a new bank account?',
    answer: 'Navigate to Accounts > Link Bank Account, choose your bank, and authenticate via your bank secure e-mandate or OTP flow.',
  ),
];

// ── Seeded Mock Transactions Available for Attachment ────────────────────────

const List<ChatAttachedTransaction> seededUserTransactions = [
  ChatAttachedTransaction(
    reference: 'TX-20261013-1001',
    amount: 2500.0,
    currency: 'LKR',
    recipientOrBiller: 'Kamal Perera',
    date: 'Today, 09:30 AM',
  ),
  ChatAttachedTransaction(
    reference: 'TX-20261013-1002',
    amount: 1200.0,
    currency: 'LKR',
    recipientOrBiller: 'Ceylon Electricity Board',
    date: 'Yesterday, 04:15 PM',
  ),
  ChatAttachedTransaction(
    reference: 'TX-20261013-1003',
    amount: 8400.0,
    currency: 'LKR',
    recipientOrBiller: 'Metro Island Grocers',
    date: '11 Oct, 02:00 PM',
  ),
];

// ── Client Masking Filter ───────────────────────────────────────────────────

String maskSensitiveText(String input) {
  if (input.isEmpty) return input;
  // Mask 13-19 digit card numbers
  final cardRegex = RegExp(r'(?<!\d)(?:\d[ -]?){12,18}\d(?!\d)');
  var result = input.replaceAll(cardRegex, '[CARD_NUMBER_MASKED]');
  // Mask 6-digit codes
  final otpRegex = RegExp(r'(?<!\d)\d{6}(?!\d)');
  result = result.replaceAll(otpRegex, '[OTP_MASKED]');
  return result;
}

// ── Riverpod State Notifiers ─────────────────────────────────────────────────

class ChatMessagesNotifier extends Notifier<List<ChatMessageItem>> {
  int _counter = 2;

  @override
  List<ChatMessageItem> build() {
    return [
      const ChatMessageItem(
        id: 'msg_0',
        sender: ChatSender.bot,
        text: 'Hello! Welcome to LankaPay 24/7 Automated Assistant. How can we help you today?\n\n'
            'Reminder: We will NEVER ask for your password, PIN, OTP, or card number.',
        time: 'Just now',
      ),
    ];
  }

  void addMessage(String text, {ChatAttachedTransaction? attachedTx}) {
    final cleanText = maskSensitiveText(text);
    final userMsg = ChatMessageItem(
      id: 'msg_${_counter++}',
      sender: ChatSender.user,
      text: cleanText,
      time: 'Just now',
      attachedTransaction: attachedTx,
    );

    state = [...state, userMsg];

    // Evaluate response
    final lower = text.toLowerCase();
    if (lower.contains('agent') || lower.contains('human') || lower.contains('representative')) {
      // Transfer to agent
      ref.read(chatStatusProvider.notifier).setStatus(ChatStatus.waiting);
      final agentAckMsg = ChatMessageItem(
        id: 'msg_${_counter++}',
        sender: ChatSender.bot,
        text: 'I am connecting you to a human support agent. A representative will review your message and reply shortly.\n\n'
            'Safety Reminder: LankaPay agents will NEVER ask for your OTP, PIN, or card number.',
        time: 'Just now',
      );
      state = [...state, agentAckMsg];
      ref.read(unreadChatCountProvider.notifier).increment();
      return;
    }

    // Check FAQ matches
    for (final faq in curatedFaqs) {
      if (lower.contains(faq.id) ||
          lower.contains(faq.title.toLowerCase()) ||
          lower.contains(faq.question.toLowerCase())) {
        final botMsg = ChatMessageItem(
          id: 'msg_${_counter++}',
          sender: ChatSender.bot,
          text: '${faq.answer}\n\nDid this help? If you need personal assistance, reply "Talk to an agent".',
          time: 'Just now',
        );
        state = [...state, botMsg];
        ref.read(unreadChatCountProvider.notifier).increment();
        return;
      }
    }

    // Default Fallback
    final fallbackMsg = ChatMessageItem(
      id: 'msg_${_counter++}',
      sender: ChatSender.bot,
      text: 'Thank you for your message. Would you like to speak to an agent or select a topic from the FAQ shortcuts above?\n\n'
          'Reply "Talk to an agent" to connect with our support team.',
      time: 'Just now',
    );
    state = [...state, fallbackMsg];
    ref.read(unreadChatCountProvider.notifier).increment();
  }

  void resolve() {
    final sysMsg = ChatMessageItem(
      id: 'msg_${_counter++}',
      sender: ChatSender.bot,
      text: 'This conversation has been marked as resolved. You can reply anytime to reopen.',
      time: 'Just now',
    );
    state = [...state, sysMsg];
  }
}

final chatMessagesProvider =
    NotifierProvider<ChatMessagesNotifier, List<ChatMessageItem>>(ChatMessagesNotifier.new);

class ChatStatusNotifier extends Notifier<ChatStatus> {
  @override
  ChatStatus build() => ChatStatus.open;

  void setStatus(ChatStatus s) => state = s;
}

final chatStatusProvider =
    NotifierProvider<ChatStatusNotifier, ChatStatus>(ChatStatusNotifier.new);

class AttachedTxNotifier extends Notifier<ChatAttachedTransaction?> {
  @override
  ChatAttachedTransaction? build() => null;

  void attach(ChatAttachedTransaction tx) => state = tx;
  void clear() => state = null;
}

final attachedTxProvider =
    NotifierProvider<AttachedTxNotifier, ChatAttachedTransaction?>(AttachedTxNotifier.new);

class UnreadChatCountNotifier extends Notifier<int> {
  @override
  int build() => 1;

  void increment() => state = state + 1;
  void reset() => state = 0;
}

final unreadChatCountProvider =
    NotifierProvider<UnreadChatCountNotifier, int>(UnreadChatCountNotifier.new);
