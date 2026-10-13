import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lankapay_id/core/test_keys.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/features/chat/domain/chat_models.dart';
import 'package:lankapay_id/features/chat/presentation/chat_screen.dart';

Widget createChatTestApp() {
  return ProviderScope(
    child: MaterialApp(
      theme: AppTheme.lightTheme,
      home: const ChatScreen(),
    ),
  );
}

void main() {
  group('Chat Tab & Support Flows Tests', () {
    testWidgets('1. Chat screen renders safety banner, status chip, FAQ shortcuts, input, attach, and send',
        (tester) async {
      tester.view.physicalSize = const Size(1920, 1080);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createChatTestApp());
      await tester.pumpAndSettle();

      // Safety banner
      expect(find.byKey(TestKeys.chatSafetyBanner), findsOneWidget);
      expect(find.textContaining('Never share your OTP or card number'), findsOneWidget);

      // Status chip
      expect(find.byKey(TestKeys.chatStatusChip), findsOneWidget);
      expect(find.text('OPEN'), findsOneWidget);

      // Controls
      expect(find.byKey(TestKeys.chatInput), findsOneWidget);
      expect(find.byKey(TestKeys.chatSend), findsOneWidget);
      expect(find.byKey(TestKeys.chatAttach), findsOneWidget);

      // FAQ Shortcuts
      expect(find.byKey(TestKeys.faq('faq_lookup')), findsOneWidget);
      expect(find.byKey(TestKeys.faq('faq_pending')), findsOneWidget);
      expect(find.byKey(TestKeys.faq('faq_freeze_card')), findsOneWidget);
      expect(find.byKey(TestKeys.faq('faq_link_account')), findsOneWidget);
    });

    testWidgets('2. Tapping FAQ shortcut sends query and triggers automated bot response',
        (tester) async {
      await tester.pumpWidget(createChatTestApp());
      await tester.pumpAndSettle();

      // Tap FAQ shortcut 'How lookup works'
      final faqChip = find.byKey(TestKeys.faq('faq_lookup'));
      await tester.tap(faqChip);
      await tester.pumpAndSettle();

      // Verify user question and bot answer appeared
      expect(find.text('How does recipient lookup work?'), findsOneWidget);
      expect(find.textContaining('central proxy registry'), findsOneWidget);
      expect(find.textContaining('Did this help?'), findsOneWidget);
    });

    testWidgets('3. Requesting agent updates status to WAITING and displays agent handoff',
        (tester) async {
      await tester.pumpWidget(createChatTestApp());
      await tester.pumpAndSettle();

      // Enter "I want to talk to an agent please"
      await tester.enterText(find.byKey(TestKeys.chatInput), 'I want to talk to a human agent please');
      await tester.tap(find.byKey(TestKeys.chatSend));
      await tester.pumpAndSettle();

      // Status chip transitions to WAITING
      expect(find.text('WAITING'), findsOneWidget);
      expect(find.textContaining('connecting you to a human support agent'), findsOneWidget);
    });

    testWidgets('4. Attach transaction picker attaches reference and displays badge in message',
        (tester) async {
      await tester.pumpWidget(createChatTestApp());
      await tester.pumpAndSettle();

      // Open attach picker
      await tester.tap(find.byKey(TestKeys.chatAttach));
      await tester.pumpAndSettle();

      expect(find.text('Attach Transaction'), findsOneWidget);

      // Tap first transaction
      final txTile = find.byKey(const Key('attach_tx_TX-20261013-1001'));
      expect(txTile, findsOneWidget);
      await tester.tap(txTile);
      await tester.pumpAndSettle();

      // Verify preview bar is visible
      expect(find.textContaining('Attached: TX-20261013-1001'), findsOneWidget);

      // Type message and send
      await tester.enterText(find.byKey(TestKeys.chatInput), 'My transfer has not reached the recipient');
      await tester.tap(find.byKey(TestKeys.chatSend));
      await tester.pumpAndSettle();

      // Attached transaction reference badge in message bubble
      expect(find.textContaining('Ref: TX-20261013-1001 (LKR 2500.00)'), findsOneWidget);
    });

    testWidgets('5. Sensitive numbers (16-digit PAN and 6-digit OTP) are masked before display',
        (tester) async {
      await tester.pumpWidget(createChatTestApp());
      await tester.pumpAndSettle();

      // Enter card number and OTP
      await tester.enterText(
        find.byKey(TestKeys.chatInput),
        'Card number 4111222233334444 and OTP 987654',
      );
      await tester.tap(find.byKey(TestKeys.chatSend));
      await tester.pumpAndSettle();

      // Check redactions
      expect(find.textContaining('4111222233334444'), findsNothing);
      expect(find.textContaining('987654'), findsNothing);
      expect(find.textContaining('[CARD_NUMBER_MASKED]'), findsOneWidget);
      expect(find.textContaining('[OTP_MASKED]'), findsOneWidget);
    });
  });
}
