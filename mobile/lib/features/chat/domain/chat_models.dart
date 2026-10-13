enum ChatSender {
  user,
  agent,
  bot,
}

enum ChatStatus {
  open,
  waiting,
  resolved,
}

class ChatAttachedTransaction {
  final String reference;
  final double amount;
  final String currency;
  final String recipientOrBiller;
  final String date;

  const ChatAttachedTransaction({
    required this.reference,
    required this.amount,
    required this.currency,
    required this.recipientOrBiller,
    required this.date,
  });
}

class ChatMessageItem {
  final String id;
  final ChatSender sender;
  final String text;
  final String time;
  final ChatAttachedTransaction? attachedTransaction;

  const ChatMessageItem({
    required this.id,
    required this.sender,
    required this.text,
    required this.time,
    this.attachedTransaction,
  });
}

class FaqShortcut {
  final String id;
  final String title;
  final String question;
  final String answer;

  const FaqShortcut({
    required this.id,
    required this.title,
    required this.question,
    required this.answer,
  });
}
