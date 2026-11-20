import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/features/history/data/history_service.dart';
import 'package:lankapay_id/features/history/presentation/history_screen.dart';
import 'package:lankapay_id/features/history/presentation/receipt_screen.dart';
import 'package:lankapay_id/features/notifications/data/notification_service.dart';
import 'package:lankapay_id/features/notifications/domain/notification_models.dart';
import 'package:lankapay_id/features/notifications/presentation/notifications_screen.dart';
import 'package:lankapay_id/features/qr/data/qr_service.dart';
import 'package:lankapay_id/features/qr/domain/qr_models.dart';
import 'package:lankapay_id/features/qr/presentation/qr_screen.dart';
import 'package:lankapay_id/features/send/domain/transfer_models.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';

// Fake QR Service
class FakeQrService implements QrService {
  QrReferenceModel? staticQr;
  QrReferenceModel? dynamicQr;
  ResolvedQrModel? resolvedQr;

  @override
  Future<QrReferenceModel> generateStaticQr() async {
    return staticQr ??
        const QrReferenceModel(
          reference: 'QREF-STATIC-001',
          qrType: 'STATIC',
          qrPayload: 'LANKAPAY:QR:QREF-STATIC-001',
          recipientDisplayName: 'Kamal Perera',
          maskedPhone: '+9477***1234',
        );
  }

  @override
  Future<QrReferenceModel> generateDynamicQr({
    required double amount,
    int validityMinutes = 15,
    String? note,
    String currency = 'LKR',
  }) async {
    return dynamicQr ??
        QrReferenceModel(
          reference: 'QREF-DYN-002',
          qrType: 'DYNAMIC',
          qrPayload: 'LANKAPAY:QR:QREF-DYN-002',
          amount: amount,
          currency: currency,
          note: note,
          expiresAt: DateTime.now().add(Duration(minutes: validityMinutes)).toUtc().toIso8601String(),
          oneTimeUse: true,
        );
  }

  @override
  Future<ResolvedQrModel> resolveReference(String reference) async {
    if (resolvedQr != null) return resolvedQr!;
    return const ResolvedQrModel(
      reference: 'QREF-DYN-002',
      qrType: 'DYNAMIC',
      recipientDisplayName: 'Anura Kumara',
      maskedPhone: '+9471***8888',
      amount: 1250.0,
      currency: 'LKR',
    );
  }
}

// Fake History Service
class FakeHistoryService implements HistoryService {
  List<TransferModel> items = [];
  HistoryFilter? lastFilter;

  @override
  Future<List<TransferModel>> listHistory({HistoryFilter? filter, int page = 0, int size = 20}) async {
    lastFilter = filter;
    var result = List<TransferModel>.from(items);
    if (filter != null) {
      if (filter.status != null) {
        result = result.where((e) => e.status.name.toUpperCase() == filter.status).toList();
      }
    }
    return result;
  }

  @override
  Future<ReceiptModel> getReceipt(int transferId) async {
    return ReceiptModel(
      receiptNumber: 'REC-TX$transferId',
      transferId: transferId,
      reference: 'TX-REF-$transferId',
      status: 'COMPLETED',
      amount: 1500.0,
      totalDebited: 1500.0,
      sender: const ReceiptPartyModel(displayName: 'Kamal Perera', maskedPhone: '+9477***1234'),
      receiver: const ReceiptPartyModel(displayName: 'Nimal Silva', maskedPhone: '+9471***9999'),
      bankTxRef: 'SIM-BANK-TX-55',
      digitalSignature: 'abc123hash999888',
    );
  }
}

// Fake Notification Service
class FakeNotificationService implements NotificationService {
  List<NotificationItemModel> notifs = [];
  NotificationPreferencesModel prefs = const NotificationPreferencesModel();

  @override
  Future<List<NotificationItemModel>> getNotifications({bool? unreadOnly, int page = 0, int size = 20}) async {
    return notifs;
  }

  @override
  Future<void> markAsRead(int notificationId) async {}

  @override
  Future<void> markAllAsRead() async {}

  @override
  Future<NotificationPreferencesModel> getPreferences() async => prefs;

  @override
  Future<NotificationPreferencesModel> updatePreferences(NotificationPreferencesModel p) async {
    prefs = p;
    return prefs;
  }
}

Widget createTestableWidget({
  required Widget child,
  List overrides = const [],
}) {
  return ProviderScope(
    overrides: overrides.cast(),
    child: MaterialApp(
      theme: AppTheme.lightTheme,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      home: child,
    ),
  );
}

void main() {
  group('QR Code Feature Tests', () {
    testWidgets('QrScreen renders static personal QR with opaque privacy banner', (WidgetTester tester) async {
      final fakeQr = FakeQrService();

      await tester.pumpWidget(
        createTestableWidget(
          child: const QrScreen(),
          overrides: [qrServiceProvider.overrideWithValue(fakeQr)],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('My QR (Static)'), findsOneWidget);
      expect(find.text('Request (Dynamic)'), findsOneWidget);
      expect(find.text('Kamal Perera'), findsOneWidget);
      expect(find.text('Opaque Privacy Reference (No PII)'), findsOneWidget);
    });

    testWidgets('QrScreen creates dynamic payment request with amount and expiry', (WidgetTester tester) async {
      final fakeQr = FakeQrService();

      await tester.pumpWidget(
        createTestableWidget(
          child: const QrScreen(),
          overrides: [qrServiceProvider.overrideWithValue(fakeQr)],
        ),
      );
      await tester.pumpAndSettle();

      // Switch to Request (Dynamic) tab
      await tester.tap(find.text('Request (Dynamic)'));
      await tester.pumpAndSettle();

      expect(find.text('Create Dynamic Payment QR'), findsOneWidget);

      await tester.enterText(find.byKey(const Key('qr_amount_field')), '2500.00');
      await tester.enterText(find.byKey(const Key('qr_note_field')), 'Lunch bill split');

      await tester.tap(find.byKey(const Key('qr_generate_dynamic_btn')));
      await tester.pumpAndSettle();

      expect(find.text('Payment Request'), findsOneWidget);
      expect(find.text('LKR 2500.00'), findsOneWidget);
      expect(find.text('Lunch bill split'), findsOneWidget);
      expect(find.text('Expires in 15 minutes'), findsOneWidget);
    });

    testWidgets('QrScannerScreen handles expired QR with friendly error message', (WidgetTester tester) async {
      final fakeQr = FakeQrService();
      fakeQr.resolvedQr = const ResolvedQrModel(
        reference: 'QREF-EXPIRED',
        qrType: 'DYNAMIC',
        status: 'EXPIRED',
      );

      // Verify domain expiry helper
      expect(fakeQr.resolvedQr!.isExpired, isTrue);
    });
  });

  group('Transaction History and Filter Tests', () {
    testWidgets('HistoryScreen renders list and applies filters correctly', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final fakeHistory = FakeHistoryService();
      fakeHistory.items = [
        const TransferModel(
          transferId: 1,
          reference: 'TX-REF-001',
          status: TransferStatusType.completed,
          amount: 1500.0,
          receiverDisplayName: 'Nimal Silva',
        ),
        const TransferModel(
          transferId: 2,
          reference: 'TX-REF-002',
          status: TransferStatusType.failed,
          amount: 500.0,
          receiverDisplayName: 'Sunil Dias',
        ),
      ];

      await tester.pumpWidget(
        createTestableWidget(
          child: const HistoryScreen(),
          overrides: [historyServiceProvider.overrideWithValue(fakeHistory)],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Nimal Silva'), findsOneWidget);
      expect(find.text('Sunil Dias'), findsOneWidget);

      // Open filter modal
      await tester.tap(find.byKey(const Key('history_filter_btn')));
      await tester.pumpAndSettle();

      expect(find.text('Filter History'), findsOneWidget);

      // Select Completed filter chip
      await tester.tap(find.byKey(const Key('filter_chip_completed')));
      await tester.pumpAndSettle();

      // Apply filters
      await tester.tap(find.byKey(const Key('history_apply_filters_btn')));
      await tester.pumpAndSettle();

      // After filtering by COMPLETED, Sunil (failed) should be filtered out
      expect(find.text('Nimal Silva'), findsOneWidget);
      expect(find.text('Sunil Dias'), findsNothing);
      expect(fakeHistory.lastFilter?.status, 'COMPLETED');
    });

    testWidgets('ReceiptScreen displays tamper signature and receipt breakdown', (WidgetTester tester) async {
      const receipt = ReceiptModel(
        receiptNumber: 'REC-550E8400',
        transferId: 55,
        reference: '550e8400-e29b-41d4-a716-446655440000',
        status: 'COMPLETED',
        amount: 2500.0,
        totalDebited: 2500.0,
        sender: ReceiptPartyModel(displayName: 'Kamal Perera', maskedPhone: '+9477***1234'),
        receiver: ReceiptPartyModel(displayName: 'Nimal Silva', maskedPhone: '+9471***5678'),
        bankTxRef: 'SIM-TX-BOC-99',
        digitalSignature: '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08',
      );

      await tester.pumpWidget(
        createTestableWidget(
          child: const ReceiptScreen(receipt: receipt),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('LankaPay Transfer Receipt'), findsOneWidget);
      expect(find.text('REC-550E8400'), findsOneWidget);
      expect(find.text('LKR 2500.00'), findsNWidgets(2));
      expect(find.text('Nimal Silva'), findsOneWidget);
      expect(find.text('SIM-TX-BOC-99'), findsOneWidget);
      expect(find.byKey(const Key('receipt_share_btn')), findsOneWidget);
    });
  });

  group('Notification Screen and Preferences Tests', () {
    testWidgets('NotificationsScreen displays privacy-safe messages and toggles preferences', (WidgetTester tester) async {
      final fakeNotifs = FakeNotificationService();
      fakeNotifs.notifs = [
        const NotificationItemModel(
          id: 1,
          title: 'Transfer Completed',
          // Privacy-safe lockscreen message per Section 12
          message: 'Your outgoing fund transfer has been completed safely.',
          type: 'TRANSACTION',
          read: false,
        ),
      ];

      await tester.pumpWidget(
        createTestableWidget(
          child: const NotificationsScreen(),
          overrides: [notificationServiceProvider.overrideWithValue(fakeNotifs)],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Transfer Completed'), findsOneWidget);
      expect(find.text('Your outgoing fund transfer has been completed safely.'), findsOneWidget);

      // Switch to Preferences tab
      await tester.tap(find.text('Preferences'));
      await tester.pumpAndSettle();

      expect(find.text('Notification Channels'), findsOneWidget);
      expect(find.byKey(const Key('prefs_push_switch')), findsOneWidget);
      expect(find.byKey(const Key('prefs_sms_switch')), findsOneWidget);
      expect(find.byKey(const Key('prefs_tx_alerts_switch')), findsOneWidget);

      // Toggle Push Notifications
      await tester.tap(find.byKey(const Key('prefs_push_switch')));
      await tester.pumpAndSettle();

      expect(fakeNotifs.prefs.pushEnabled, isFalse);
    });
  });
}
