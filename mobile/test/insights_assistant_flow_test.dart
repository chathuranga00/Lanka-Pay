import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lankapay_id/core/test_keys.dart';
import 'package:lankapay_id/features/analytics/data/insights_service.dart';
import 'package:lankapay_id/features/analytics/domain/insight_model.dart';
import 'package:lankapay_id/features/chat/data/chat_service.dart';
import 'package:lankapay_id/features/chat/presentation/chat_screen.dart';
import 'package:lankapay_id/features/history/data/history_service.dart';
import 'package:lankapay_id/features/history/presentation/history_screen.dart';
import 'package:lankapay_id/features/send/domain/transfer_models.dart';

class FakeHistoryService implements HistoryService {
  @override
  Future<List<TransferModel>> listHistory({HistoryFilter? filter, int page = 0, int size = 20}) async {
    return [];
  }

  @override
  Future<ReceiptModel> getReceipt(int transferId) async {
    throw UnimplementedError();
  }
}

void main() {
  setUp(() {
    TestWidgetsFlutterBinding.ensureInitialized();
  });

  Widget buildTestApp(Widget child) {
    return ProviderScope(
      overrides: [
        historyServiceProvider.overrideWithValue(FakeHistoryService()),
      ],
      child: MaterialApp(
        home: child,
      ),
    );
  }

  group('Insights Flow on Activity/History Screen', () {
    testWidgets('Activity screen displays Financial Insights card with factual insights',
        (tester) async {
      tester.view.physicalSize = const Size(1920, 1080);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.pumpWidget(buildTestApp(const HistoryScreen()));
      await tester.pumpAndSettle();

      // Verify Insights card exists
      expect(find.byKey(TestKeys.activityInsightsCard), findsOneWidget);
      expect(find.text('Financial Insights'), findsOneWidget);
      expect(find.text('Factual'), findsOneWidget);

      // Verify factual insight texts
      expect(find.text('Spending Decreased vs Last Month'), findsOneWidget);
      expect(find.text('Monthly Budget Nearing Limit'), findsOneWidget);
    });

    testWidgets('Dismissing an insight removes it from the Insights card', (tester) async {
      tester.view.physicalSize = const Size(1920, 1080);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.pumpWidget(buildTestApp(const HistoryScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Spending Decreased vs Last Month'), findsOneWidget);

      // Dismiss the SPENDING_CHANGE insight
      final dismissBtn = find.byKey(TestKeys.insightDismiss('SPENDING_CHANGE'));
      expect(dismissBtn, findsOneWidget);
      await tester.tap(dismissBtn);
      await tester.pumpAndSettle();

      // Verified removed
      expect(find.text('Spending Decreased vs Last Month'), findsNothing);
      // Other insights remain
      expect(find.text('Monthly Budget Nearing Limit'), findsOneWidget);
    });
  });

  group('AI Assistant Flow inside Chat Screen', () {
    testWidgets('Switching to AI assistant shows Consent View first when not consented',
        (tester) async {
      tester.view.physicalSize = const Size(1920, 1080);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.pumpWidget(buildTestApp(const ChatScreen()));
      await tester.pumpAndSettle();

      // Initially in Support mode
      expect(find.byKey(TestKeys.chatSafetyBanner), findsOneWidget);
      expect(find.byKey(TestKeys.chatAskAi), findsOneWidget);

      // Tap Ask AI
      await tester.tap(find.byKey(TestKeys.chatAskAi));
      await tester.pumpAndSettle();

      // Consent view must be displayed first
      expect(find.byKey(TestKeys.assistantConsentView), findsOneWidget);
      expect(find.text('LankaPay AI Assistant'), findsOneWidget);
      expect(find.byKey(TestKeys.assistantEnableBtn), findsOneWidget);
    });

    testWidgets('Enabling consent opens AI conversation, responds with disclaimer and feedback',
        (tester) async {
      tester.view.physicalSize = const Size(1920, 1080);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.pumpWidget(buildTestApp(const ChatScreen()));
      await tester.pumpAndSettle();

      // Switch to AI
      await tester.tap(find.byKey(TestKeys.chatAskAi));
      await tester.pumpAndSettle();

      // Tap Enable AI Assistant
      await tester.tap(find.byKey(TestKeys.assistantEnableBtn));
      await tester.pumpAndSettle();

      // Now in conversation mode
      expect(find.byKey(TestKeys.assistantInput), findsOneWidget);
      expect(find.byKey(TestKeys.assistantSend), findsOneWidget);
      expect(find.byKey(TestKeys.assistantAiDisclaimer), findsWidgets);
      expect(find.text('AI-generated, may be wrong'), findsWidgets);

      // Ask question
      await tester.enterText(find.byKey(TestKeys.assistantInput), 'How do I send money?');
      await tester.tap(find.byKey(TestKeys.assistantSend));
      await tester.pumpAndSettle();

      // Check reply
      expect(find.textContaining('To send money, tap "Send" from Home'), findsOneWidget);
      expect(find.byKey(TestKeys.assistantThumbsUp), findsWidgets);
      expect(find.byKey(TestKeys.assistantThumbsDown), findsWidgets);

      // Tap Thumbs up feedback
      await tester.tap(find.byKey(TestKeys.assistantThumbsUp).last);
      await tester.pumpAndSettle();
    });

    testWidgets('AI Assistant refuses to move money or change settings', (tester) async {
      tester.view.physicalSize = const Size(1920, 1080);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      await tester.pumpWidget(buildTestApp(const ChatScreen()));
      await tester.pumpAndSettle();

      // Switch to AI
      await tester.tap(find.byKey(TestKeys.chatAskAi));
      await tester.pumpAndSettle();

      // Enable consent
      await tester.tap(find.byKey(TestKeys.assistantEnableBtn));
      await tester.pumpAndSettle();

      // Ask to move money
      await tester.enterText(find.byKey(TestKeys.assistantInput), 'Send money to John please LKR 5000');
      await tester.tap(find.byKey(TestKeys.assistantSend));
      await tester.pumpAndSettle();

      expect(find.textContaining('cannot move money, make transfers, or process payments'), findsOneWidget);
      expect(find.text('AI-generated, may be wrong'), findsWidgets);
    });
  });
}
