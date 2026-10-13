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

// ── AI Assistant State & Logic ────────────────────────────────────────────────

class AssistantMessage {
  final String id;
  final bool isUser;
  final String text;
  final String? disclaimer;
  final List<String> suggestedActions;
  final bool? thumbsUp;

  const AssistantMessage({
    required this.id,
    required this.isUser,
    required this.text,
    this.disclaimer,
    this.suggestedActions = const [],
    this.thumbsUp,
  });

  AssistantMessage copyWith({bool? thumbsUp}) {
    return AssistantMessage(
      id: id,
      isUser: isUser,
      text: text,
      disclaimer: disclaimer,
      suggestedActions: suggestedActions,
      thumbsUp: thumbsUp ?? this.thumbsUp,
    );
  }
}

class AssistantConsentNotifier extends Notifier<bool> {
  @override
  bool build() => false; // Disabled by default!

  void setConsent(bool consented) => state = consented;
}

final assistantConsentProvider =
    NotifierProvider<AssistantConsentNotifier, bool>(AssistantConsentNotifier.new);

class AssistantMessagesNotifier extends Notifier<List<AssistantMessage>> {
  int _counter = 1;

  @override
  List<AssistantMessage> build() {
    return [
      const AssistantMessage(
        id: 'ast_0',
        isUser: false,
        text: 'Hello! I am your LankaPay AI Assistant. I can explain app features or summarize your aggregated insights.',
        disclaimer: 'AI-generated, may be wrong',
      ),
    ];
  }

  void ask(String userQuery) {
    final cleanQuery = maskSensitiveText(userQuery);
    final userMsg = AssistantMessage(
      id: 'ast_${_counter++}',
      isUser: true,
      text: cleanQuery,
    );
    state = [...state, userMsg];

    // Evaluate answer with guardrails
    final lower = cleanQuery.toLowerCase();
    String answer;
    List<String> actions = [];

    if (lower.contains('send money to') || lower.contains('pay my') || lower.contains('transfer to')) {
      answer = 'I am an AI assistant and cannot move money, make transfers, or process payments on your behalf. '
          'Please use the Send Money, Pay Bills, or QR tabs to complete transactions securely.';
      actions = ['/pay/send'];
    } else if (lower.contains('change my pin') || lower.contains('freeze my card') || lower.contains('change settings')) {
      answer = 'I cannot change account settings or security credentials. '
          'Please manage your security preferences directly in Settings or Cards.';
      actions = ['/wallet/cards'];
    } else if (lower.contains('invest') || lower.contains('stock') || lower.contains('tax')) {
      answer = 'I cannot provide investment, tax, or legal financial advice. '
          'I can only provide factual summaries of app features and your aggregated insights.';
    } else if (lower.contains('insight') || lower.contains('summary') || lower.contains('spend') || lower.contains('overview')) {
      answer = 'Based on your aggregated data: Spending is 14.2% lower this month, and you have used 82.5% of your monthly budget limit.';
      actions = ['/analytics'];
    } else if (lower.contains('send') || lower.contains('transfer')) {
      answer = 'To send money, tap "Send" from Home. You can transfer funds using LankaPay ID, account number, or mobile number.';
      actions = ['/pay/send'];
    } else if (lower.contains('split')) {
      answer = 'Use the "Split Bill" feature to divide shared payments with contacts and request their shares directly.';
      actions = ['/pay/split'];
    } else {
      answer = 'I can explain features like QR payments, bill payments, scheduled transfers, cards, and summarize your spending insights. What would you like to explore?';
    }

    final aiMsg = AssistantMessage(
      id: 'ast_${_counter++}',
      isUser: false,
      text: answer,
      disclaimer: 'AI-generated, may be wrong',
      suggestedActions: actions,
    );

    state = [...state, aiMsg];
  }

  void setFeedback(String messageId, bool thumbsUp) {
    state = state.map((m) {
      if (m.id == messageId) {
        return m.copyWith(thumbsUp: thumbsUp);
      }
      return m;
    }).toList();
  }
}

final assistantMessagesProvider =
    NotifierProvider<AssistantMessagesNotifier, List<AssistantMessage>>(AssistantMessagesNotifier.new);
