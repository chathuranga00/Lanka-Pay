import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_tokens.dart';

/// Chat Tab (Tab 3: Chat).
/// In-app customer support chat with automated banking FAQ assistant.
class ChatScreen extends ConsumerStatefulWidget {
  const ChatScreen({super.key = const Key('chat_screen')});

  @override
  ConsumerState<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends ConsumerState<ChatScreen> {
  final TextEditingController _msgController = TextEditingController();
  final List<Map<String, String>> _messages = [
    {
      'sender': 'agent',
      'text': 'Hello Kasun! Welcome to LankaPay 24/7 Support. How can we help you today with your accounts, cards, or transfers?',
      'time': '10:30 AM',
    },
  ];

  @override
  void dispose() {
    _msgController.dispose();
    super.dispose();
  }

  void _sendMessage([String? text]) {
    final msg = text ?? _msgController.text.trim();
    if (msg.isEmpty) return;

    setState(() {
      _messages.add({
        'sender': 'user',
        'text': msg,
        'time': 'Just now',
      });
      if (text == null) _msgController.clear();
    });

    // Simulate smart bot response
    Future.delayed(const Duration(milliseconds: 600), () {
      if (!mounted) return;
      setState(() {
        String reply = 'Thank you for reaching out. We have logged your inquiry. A simulated support agent is reviewing your request.';
        final lower = msg.toLowerCase();
        if (lower.contains('limit') || lower.contains('cefts')) {
          reply = 'LankaPay CEFTS transfers support up to LKR 5,000,000 per transaction depending on your daily tier. You can review your limits under Settings > Security.';
        } else if (lower.contains('freeze') || lower.contains('card')) {
          reply = 'You can instantly freeze or unfreeze your cards from the Cards section on the Home or Explore tab with your Step-Up PIN.';
        }
        _messages.add({
          'sender': 'agent',
          'text': reply,
          'time': 'Just now',
        });
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    final quickFaqs = [
      'How do I freeze my card?',
      'What are LankaQR transfer limits?',
      'How long does CEFTS settlement take?',
    ];

    return Scaffold(
      key: const Key('chat_screen'),
      backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
      appBar: AppBar(
        title: Row(
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: AppTokens.primaryBlue,
                borderRadius: BorderRadius.circular(10),
              ),
              child: const Icon(Icons.support_agent_rounded, color: Colors.white, size: 22),
            ),
            const SizedBox(width: 12),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Support & Help',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                ),
                Text(
                  'Online • Automated Assistant',
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w500,
                    color: isDark ? AppTokens.success : const Color(0xFF16A34A),
                  ),
                ),
              ],
            ),
          ],
        ),
        backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
        elevation: 0,
        centerTitle: false,
      ),
      body: Column(
        children: [
          // Message history
          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              itemCount: _messages.length,
              itemBuilder: (context, index) {
                final m = _messages[index];
                final isUser = m['sender'] == 'user';
                return Align(
                  alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
                  child: Container(
                    margin: const EdgeInsets.only(bottom: 10),
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                    constraints: BoxConstraints(
                      maxWidth: MediaQuery.of(context).size.width * 0.78,
                    ),
                    decoration: BoxDecoration(
                      color: isUser
                          ? AppTokens.primaryBlue
                          : (isDark ? AppTokens.cardDark : AppTokens.searchFieldBgLight),
                      borderRadius: BorderRadius.only(
                        topLeft: const Radius.circular(18),
                        topRight: const Radius.circular(18),
                        bottomLeft: Radius.circular(isUser ? 18 : 4),
                        bottomRight: Radius.circular(isUser ? 4 : 18),
                      ),
                    ),
                    child: Column(
                      crossAxisAlignment:
                          isUser ? CrossAxisAlignment.end : CrossAxisAlignment.start,
                      children: [
                        Text(
                          m['text']!,
                          style: TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            fontSize: 14,
                            color: isUser
                                ? Colors.white
                                : (isDark
                                    ? AppTokens.textPrimaryDark
                                    : AppTokens.textPrimaryLight),
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          m['time']!,
                          style: TextStyle(
                            fontSize: 10,
                            color: isUser
                                ? Colors.white70
                                : (isDark
                                    ? AppTokens.textTertiaryDark
                                    : AppTokens.textTertiaryLight),
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),

          // FAQ suggestions
          SizedBox(
            height: 38,
            child: ListView.separated(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              scrollDirection: Axis.horizontal,
              itemCount: quickFaqs.length,
              separatorBuilder: (_, __) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final faq = quickFaqs[index];
                return ActionChip(
                  label: Text(
                    faq,
                    style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w500),
                  ),
                  backgroundColor: isDark ? AppTokens.cardDark : AppTokens.tileTintLight,
                  onPressed: () => _sendMessage(faq),
                );
              },
            ),
          ),
          const SizedBox(height: 8),

          // Input field
          Container(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
            decoration: BoxDecoration(
              color: isDark ? AppTokens.cardDark : Colors.white,
              border: Border(
                top: BorderSide(
                  color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                  width: 0.8,
                ),
              ),
            ),
            child: SafeArea(
              top: false,
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      key: const Key('chat_input_field'),
                      controller: _msgController,
                      decoration: InputDecoration(
                        hintText: 'Type your message...',
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(20),
                          borderSide: BorderSide.none,
                        ),
                        filled: true,
                        fillColor: isDark
                            ? AppTokens.searchFieldBgDark
                            : AppTokens.searchFieldBgLight,
                        contentPadding:
                            const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                      ),
                      onSubmitted: (_) => _sendMessage(),
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton(
                    key: const Key('chat_send_btn'),
                    icon: const Icon(Icons.send_rounded, color: AppTokens.primaryBlue),
                    onPressed: () => _sendMessage(),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
