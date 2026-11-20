import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/test_keys.dart';
import '../../../core/theme/app_tokens.dart';
import '../data/chat_service.dart';
import '../domain/chat_models.dart';

class ChatScreen extends ConsumerStatefulWidget {
  const ChatScreen({super.key = const Key('chat_screen')});

  @override
  ConsumerState<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends ConsumerState<ChatScreen> {
  final TextEditingController _msgController = TextEditingController();
  final TextEditingController _assistantController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  bool _isAiMode = false;

  @override
  void dispose() {
    _msgController.dispose();
    _assistantController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
      }
    });
  }

  void _sendMessage([String? presetText]) {
    final text = presetText ?? _msgController.text.trim();
    if (text.isEmpty) return;

    final attachedTx = ref.read(attachedTxProvider);
    ref.read(chatMessagesProvider.notifier).addMessage(text, attachedTx: attachedTx);
    ref.read(attachedTxProvider.notifier).clear();

    if (presetText == null) {
      _msgController.clear();
    }

    _scrollToBottom();
  }

  void _sendAssistantMessage([String? presetText]) {
    final text = presetText ?? _assistantController.text.trim();
    if (text.isEmpty) return;

    ref.read(assistantMessagesProvider.notifier).ask(text);
    if (presetText == null) {
      _assistantController.clear();
    }
    _scrollToBottom();
  }

  void _showTransactionPicker() {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) {
        final isDark = Theme.of(ctx).brightness == Brightness.dark;
        return SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(vertical: 16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Attach Transaction',
                        style: TextStyle(
                          fontFamily: AppTokens.fontFamily,
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                          color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close_rounded, size: 20),
                        onPressed: () => Navigator.pop(ctx),
                      ),
                    ],
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 4),
                  child: Text(
                    'Select a recent transaction reference to share with support:',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 12,
                      color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                ...seededUserTransactions.map((tx) {
                  return ListTile(
                    key: Key('attach_tx_${tx.reference}'),
                    leading: Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: AppTokens.tileTintLight,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: const Icon(Icons.receipt_long_rounded, color: AppTokens.primaryBlue, size: 22),
                    ),
                    title: Text(
                      '${tx.currency} ${tx.amount.toStringAsFixed(2)} • ${tx.recipientOrBiller}',
                      style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                    ),
                    subtitle: Text(
                      'Ref: ${tx.reference} • ${tx.date}',
                      style: const TextStyle(fontSize: 12),
                    ),
                    onTap: () {
                      ref.read(attachedTxProvider.notifier).attach(tx);
                      Navigator.pop(ctx);
                    },
                  );
                }),
              ],
            ),
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final messages = ref.watch(chatMessagesProvider);
    final status = ref.watch(chatStatusProvider);
    final attachedTx = ref.watch(attachedTxProvider);

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
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Support & Help',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                  ),
                  Row(
                    children: [
                      Container(
                        width: 7,
                        height: 7,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: status == ChatStatus.resolved
                              ? Colors.grey
                              : (status == ChatStatus.waiting ? Colors.amber : Colors.green),
                        ),
                      ),
                      const SizedBox(width: 5),
                      Text(
                        status == ChatStatus.waiting
                            ? 'Waiting for Agent'
                            : (status == ChatStatus.resolved ? 'Resolved' : 'Online • Bot Assistant'),
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w500,
                          color: status == ChatStatus.waiting
                              ? Colors.amber
                              : (status == ChatStatus.resolved
                                  ? AppTokens.textTertiaryLight
                                  : (isDark ? AppTokens.success : const Color(0xFF16A34A))),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
        actions: [
          // Ask AI Assistant Toggle
          IconButton(
            key: TestKeys.chatAskAi,
            icon: Icon(
              _isAiMode ? Icons.support_agent_rounded : Icons.auto_awesome_rounded,
              color: _isAiMode ? Colors.amber : AppTokens.primaryBlue,
            ),
            tooltip: _isAiMode ? 'Switch to Support' : 'Ask AI Assistant',
            onPressed: () {
              setState(() => _isAiMode = !_isAiMode);
            },
          ),

          // Status Chip
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: Center(
              child: Container(
                key: TestKeys.chatStatusChip,
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: status == ChatStatus.resolved
                      ? Colors.grey.withOpacity(0.15)
                      : (status == ChatStatus.waiting
                          ? Colors.amber.withOpacity(0.15)
                          : AppTokens.primaryBlue.withOpacity(0.15)),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: status == ChatStatus.resolved
                        ? Colors.grey
                        : (status == ChatStatus.waiting ? Colors.amber : AppTokens.primaryBlue),
                    width: 0.8,
                  ),
                ),
                child: Text(
                  status.name.toUpperCase(),
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    color: status == ChatStatus.resolved
                        ? (isDark ? Colors.white70 : Colors.black54)
                        : (status == ChatStatus.waiting ? Colors.amber : AppTokens.primaryBlue),
                  ),
                ),
              ),
            ),
          ),
        ],
        backgroundColor: isDark ? AppTokens.backgroundDark : AppTokens.backgroundLight,
        elevation: 0,
        centerTitle: false,
      ),
      body: _isAiMode
          ? _buildAssistantMode(isDark)
          : Column(
              children: [
                // ── Safety Warning Banner ──────────────────────────────────────────
                Container(
                  key: TestKeys.chatSafetyBanner,
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            color: isDark ? const Color(0xFF2D1616) : const Color(0xFFFEF2F2),
            child: Row(
              children: [
                const Icon(Icons.shield_outlined, color: AppTokens.error, size: 18),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    'Never share your OTP or card number. LankaPay staff will never ask for your PIN, OTP, or full card details.',
                    style: TextStyle(
                      fontFamily: AppTokens.fontFamily,
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: isDark ? const Color(0xFFFCA5A5) : const Color(0xFFDC2626),
                    ),
                  ),
                ),
              ],
            ),
          ),

          // ── Message Thread ────────────────────────────────────────────────
          Expanded(
            child: ListView.builder(
              controller: _scrollController,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              itemCount: messages.length,
              itemBuilder: (context, index) {
                final m = messages[index];
                final isUser = m.sender == ChatSender.user;
                final isAgent = m.sender == ChatSender.agent;

                return Align(
                  alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
                  child: Container(
                    key: TestKeys.chatMsg(m.id),
                    margin: const EdgeInsets.only(bottom: 12),
                    constraints: BoxConstraints(
                      maxWidth: MediaQuery.of(context).size.width * 0.82,
                    ),
                    child: Column(
                      crossAxisAlignment:
                          isUser ? CrossAxisAlignment.end : CrossAxisAlignment.start,
                      children: [
                        // Sender Indicator
                        Padding(
                          padding: const EdgeInsets.only(bottom: 3, left: 4, right: 4),
                          child: Text(
                            isUser ? 'You' : (isAgent ? 'Support Agent' : 'LankaPay Assistant'),
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                              color: isUser
                                  ? AppTokens.primaryBlue
                                  : (isAgent ? Colors.green : Colors.amber.shade800),
                            ),
                          ),
                        ),

                        // Bubble
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                          decoration: BoxDecoration(
                            color: isUser
                                ? AppTokens.primaryBlue
                                : (isDark ? AppTokens.cardDark : AppTokens.searchFieldBgLight),
                            borderRadius: BorderRadius.only(
                              topLeft: const Radius.circular(16),
                              topRight: const Radius.circular(16),
                              bottomLeft: Radius.circular(isUser ? 16 : 4),
                              bottomRight: Radius.circular(isUser ? 4 : 16),
                            ),
                            border: isUser
                                ? null
                                : Border.all(
                                    color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                                    width: 0.6,
                                  ),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              // Attached Transaction Badge if present
                              if (m.attachedTransaction != null) ...[
                                Container(
                                  padding: const EdgeInsets.all(8),
                                  margin: const EdgeInsets.only(bottom: 8),
                                  decoration: BoxDecoration(
                                    color: isUser
                                        ? Colors.white.withOpacity(0.15)
                                        : AppTokens.tileTintLight,
                                    borderRadius: BorderRadius.circular(8),
                                  ),
                                  child: Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      Icon(
                                        Icons.receipt_rounded,
                                        size: 16,
                                        color: isUser ? Colors.white : AppTokens.primaryBlue,
                                      ),
                                      const SizedBox(width: 6),
                                      Text(
                                        'Ref: ${m.attachedTransaction!.reference} (${m.attachedTransaction!.currency} ${m.attachedTransaction!.amount.toStringAsFixed(2)})',
                                        style: TextStyle(
                                          fontSize: 11,
                                          fontWeight: FontWeight.w700,
                                          color: isUser ? Colors.white : AppTokens.primaryBlue,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],

                              Text(
                                m.text,
                                style: TextStyle(
                                  fontFamily: AppTokens.fontFamily,
                                  fontSize: 13.5,
                                  color: isUser
                                      ? Colors.white
                                      : (isDark
                                          ? AppTokens.textPrimaryDark
                                          : AppTokens.textPrimaryLight),
                                  height: 1.35,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Align(
                                alignment: Alignment.bottomRight,
                                child: Text(
                                  m.time,
                                  style: TextStyle(
                                    fontSize: 10,
                                    color: isUser
                                        ? Colors.white70
                                        : (isDark
                                            ? AppTokens.textTertiaryDark
                                            : AppTokens.textTertiaryLight),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),

          // ── FAQ Shortcuts Row ──────────────────────────────────────────────
          Container(
            height: 38,
            margin: const EdgeInsets.only(bottom: 6),
            child: ListView(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              scrollDirection: Axis.horizontal,
              children: [
                ...curatedFaqs.map((faq) {
                  return Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: ActionChip(
                      key: TestKeys.faq(faq.id),
                      avatar: const Icon(Icons.help_outline_rounded, size: 14, color: AppTokens.primaryBlue),
                      label: Text(
                        faq.title,
                        style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                      ),
                      backgroundColor: isDark ? AppTokens.cardDark : AppTokens.tileTintLight,
                      onPressed: () => _sendMessage(faq.question),
                    ),
                  );
                }),
                Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ActionChip(
                    avatar: const Icon(Icons.headset_mic_rounded, size: 14, color: Colors.amber),
                    label: const Text(
                      'Talk to an agent',
                      style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                    ),
                    backgroundColor: isDark ? AppTokens.cardDark : Colors.amber.withOpacity(0.12),
                    onPressed: () => _sendMessage('Talk to an agent'),
                  ),
                ),
              ],
            ),
          ),

          // ── Attached Tx Preview Bar (if active) ────────────────────────────
          if (attachedTx != null)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
              color: isDark ? AppTokens.cardDark : AppTokens.tileTintLight,
              child: Row(
                children: [
                  const Icon(Icons.attach_file_rounded, size: 16, color: AppTokens.primaryBlue),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      'Attached: ${attachedTx.reference} (${attachedTx.currency} ${attachedTx.amount.toStringAsFixed(2)})',
                      style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppTokens.primaryBlue),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.cancel_rounded, size: 18, color: Colors.grey),
                    onPressed: () {
                      ref.read(attachedTxProvider.notifier).clear();
                    },
                  ),
                ],
              ),
            ),

          // ── Input & Attachment Bar ─────────────────────────────────────────
          Container(
            padding: const EdgeInsets.fromLTRB(12, 8, 12, 16),
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
                  // Attach Transaction Button
                  IconButton(
                    key: TestKeys.chatAttach,
                    icon: Icon(
                      attachedTx != null ? Icons.attach_file_rounded : Icons.add_circle_outline_rounded,
                      color: attachedTx != null ? AppTokens.primaryBlue : Colors.grey,
                      size: 24,
                    ),
                    tooltip: 'Attach transaction',
                    onPressed: _showTransactionPicker,
                  ),
                  const SizedBox(width: 4),

                  // Message Input
                  Expanded(
                    child: TextField(
                      key: TestKeys.chatInput,
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

                  // Send Button
                  IconButton(
                    key: TestKeys.chatSend,
                    icon: const Icon(Icons.send_rounded, color: AppTokens.primaryBlue),
                    tooltip: 'Send message',
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

  Widget _buildAssistantMode(bool isDark) {
    final hasConsent = ref.watch(assistantConsentProvider);
    if (!hasConsent) {
      return _buildAssistantConsentView(isDark);
    }
    return _buildAssistantChatView(isDark);
  }

  Widget _buildAssistantConsentView(bool isDark) {
    return Center(
      child: SingleChildScrollView(
        key: TestKeys.assistantConsentView,
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: AppTokens.primaryBlue.withOpacity(0.12),
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.auto_awesome_rounded, size: 40, color: AppTokens.primaryBlue),
            ),
            const SizedBox(height: 18),
            Text(
              'LankaPay AI Assistant',
              style: TextStyle(
                fontFamily: AppTokens.fontFamily,
                fontSize: 20,
                fontWeight: FontWeight.w800,
                color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              'LankaPay AI Assistant can explain app features and summarize your high-level spending insights. The assistant is disabled by default.\n\n'
              'Privacy & Safety Rules:\n'
              '• No card numbers, passwords, PINs, or OTPs are ever shared.\n'
              '• Exact bank balances and support conversations are never sent.\n'
              '• Prompts and answers are never logged or stored.\n'
              '• Informational facts only; never financial or legal advice.\n'
              '• You can revoke consent at any time.',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 13,
                height: 1.45,
                color: isDark ? AppTokens.textSecondaryDark : AppTokens.textSecondaryLight,
              ),
            ),
            const SizedBox(height: 24),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  'Enable AI Assistant',
                  style: TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 14,
                    color: isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight,
                  ),
                ),
                const SizedBox(width: 10),
                Switch(
                  key: TestKeys.assistantConsentToggle,
                  value: false,
                  onChanged: (val) {
                    ref.read(assistantConsentProvider.notifier).setConsent(val);
                  },
                ),
              ],
            ),
            const SizedBox(height: 12),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton(
                key: TestKeys.assistantEnableBtn,
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTokens.primaryBlue,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
                onPressed: () {
                  ref.read(assistantConsentProvider.notifier).setConsent(true);
                },
                child: const Text('Enable AI Assistant', style: TextStyle(fontWeight: FontWeight.w700)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAssistantChatView(bool isDark) {
    final assistantMessages = ref.watch(assistantMessagesProvider);

    return Column(
      children: [
        // Quick Prompts Row
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: Row(
            children: [
              _buildAssistantQuickChip('How do I send money?', isDark),
              const SizedBox(width: 8),
              _buildAssistantQuickChip('Summarise my insights', isDark),
              const SizedBox(width: 8),
              _buildAssistantQuickChip('How do I split a bill?', isDark),
            ],
          ),
        ),

        // Message Thread
        Expanded(
          child: ListView.builder(
            controller: _scrollController,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            itemCount: assistantMessages.length,
            itemBuilder: (context, index) {
              final m = assistantMessages[index];
              return _buildAssistantMessageBubble(m, isDark);
            },
          ),
        ),

        // Assistant Input Bar
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          decoration: BoxDecoration(
            color: isDark ? AppTokens.cardDark : Colors.white,
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.04),
                blurRadius: 4,
                offset: const Offset(0, -2),
              ),
            ],
          ),
          child: SafeArea(
            top: false,
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    key: TestKeys.assistantInput,
                    controller: _assistantController,
                    decoration: InputDecoration(
                      hintText: 'Ask about app features or insights...',
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
                    onSubmitted: (_) => _sendAssistantMessage(),
                  ),
                ),
                const SizedBox(width: 8),
                IconButton(
                  key: TestKeys.assistantSend,
                  icon: const Icon(Icons.send_rounded, color: AppTokens.primaryBlue),
                  tooltip: 'Send question',
                  onPressed: () => _sendAssistantMessage(),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildAssistantQuickChip(String label, bool isDark) {
    return ActionChip(
      label: Text(label, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
      backgroundColor: isDark ? AppTokens.searchFieldBgDark : AppTokens.tileTintLight,
      onPressed: () => _sendAssistantMessage(label),
    );
  }

  Widget _buildAssistantMessageBubble(AssistantMessage m, bool isDark) {
    final isUser = m.isUser;

    return Align(
      alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(bottom: 12),
        constraints: BoxConstraints(
          maxWidth: MediaQuery.of(context).size.width * 0.85,
        ),
        child: Column(
          crossAxisAlignment: isUser ? CrossAxisAlignment.end : CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.only(bottom: 3, left: 4, right: 4),
              child: Text(
                isUser ? 'You' : 'LankaPay AI Assistant',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                  color: isUser ? AppTokens.primaryBlue : AppTokens.primaryViolet,
                ),
              ),
            ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: isUser
                    ? AppTokens.primaryBlue
                    : (isDark ? AppTokens.cardDark : AppTokens.searchFieldBgLight),
                borderRadius: BorderRadius.only(
                  topLeft: const Radius.circular(16),
                  topRight: const Radius.circular(16),
                  bottomLeft: Radius.circular(isUser ? 16 : 4),
                  bottomRight: Radius.circular(isUser ? 4 : 16),
                ),
                border: isUser
                    ? null
                    : Border.all(
                        color: isDark ? AppTokens.borderDark : AppTokens.borderLight,
                        width: 0.6,
                      ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    m.text,
                    style: TextStyle(
                      fontSize: 14,
                      height: 1.35,
                      color: isUser
                          ? Colors.white
                          : (isDark ? AppTokens.textPrimaryDark : AppTokens.textPrimaryLight),
                    ),
                  ),
                  if (!isUser && m.disclaimer != null) ...[
                    const SizedBox(height: 8),
                    Container(
                      key: TestKeys.assistantAiDisclaimer,
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: Colors.amber.withOpacity(0.15),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text(
                        m.disclaimer!,
                        style: const TextStyle(
                          fontSize: 10,
                          fontWeight: FontWeight.w700,
                          color: Colors.amber,
                        ),
                      ),
                    ),
                    const SizedBox(height: 6),
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Text(
                          'Was this helpful?',
                          style: TextStyle(fontSize: 11, color: Colors.grey),
                        ),
                        const SizedBox(width: 8),
                        IconButton(
                          key: TestKeys.assistantThumbsUp,
                          icon: Icon(
                            m.thumbsUp == true ? Icons.thumb_up_rounded : Icons.thumb_up_alt_outlined,
                            size: 15,
                            color: m.thumbsUp == true ? AppTokens.primaryBlue : Colors.grey,
                          ),
                          padding: EdgeInsets.zero,
                          constraints: const BoxConstraints(),
                          onPressed: () {
                            HapticFeedback.lightImpact();
                            ref.read(assistantMessagesProvider.notifier).setFeedback(m.id, true);
                          },
                        ),
                        const SizedBox(width: 12),
                        IconButton(
                          key: TestKeys.assistantThumbsDown,
                          icon: Icon(
                            m.thumbsUp == false ? Icons.thumb_down_rounded : Icons.thumb_down_alt_outlined,
                            size: 15,
                            color: m.thumbsUp == false ? AppTokens.error : Colors.grey,
                          ),
                          padding: EdgeInsets.zero,
                          constraints: const BoxConstraints(),
                          onPressed: () {
                            HapticFeedback.lightImpact();
                            ref.read(assistantMessagesProvider.notifier).setFeedback(m.id, false);
                          },
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
