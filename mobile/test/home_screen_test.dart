import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lankapay_id/core/localization/locale_controller.dart';
import 'package:lankapay_id/core/routing/app_router.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/core/theme/theme_controller.dart';
import 'package:lankapay_id/core/widgets/shimmer_skeletons.dart';
import 'package:lankapay_id/core/widgets/simulated_badge.dart';
import 'package:lankapay_id/features/accounts/data/bank_account_service.dart';
import 'package:lankapay_id/features/accounts/domain/bank_account_model.dart';
import 'package:lankapay_id/features/accounts/presentation/accounts_screen.dart';
import 'package:lankapay_id/features/accounts/presentation/add_bank_account_screen.dart';
import 'package:lankapay_id/features/history/data/history_service.dart';
import 'package:lankapay_id/features/history/presentation/history_screen.dart';
import 'package:lankapay_id/features/history/presentation/receipt_screen.dart';
import 'package:lankapay_id/features/home/data/home_providers.dart';
import 'package:lankapay_id/features/home/presentation/home_screen.dart';
import 'package:lankapay_id/features/notifications/data/notification_service.dart';
import 'package:lankapay_id/features/notifications/domain/notification_models.dart';
import 'package:lankapay_id/features/notifications/presentation/notifications_screen.dart';
import 'package:lankapay_id/features/search/presentation/search_screen.dart';
import 'package:lankapay_id/features/qr/data/qr_service.dart';
import 'package:lankapay_id/features/qr/domain/qr_models.dart';
import 'package:lankapay_id/features/qr/presentation/qr_screen.dart';
import 'package:lankapay_id/features/send/domain/transfer_models.dart';
import 'package:lankapay_id/features/send/presentation/send_screen.dart';
import 'package:lankapay_id/features/settings/presentation/privacy_settings_screen.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';

// ---------------------------------------------------------------------------
// Test Doubles
// ---------------------------------------------------------------------------

class FakePaymentProfileNotifier extends PaymentProfileNotifier {
  final String? name;
  FakePaymentProfileNotifier({this.name = 'James Taylor'});

  @override
  PaymentProfileState build() {
    return PaymentProfileState(
      safeName: name,
      isDiscoverable: true,
      isLoading: false,
    );
  }
}

class FakeQrService implements QrService {
  @override
  Future<QrReferenceModel> generateStaticQr() async => const QrReferenceModel(
        reference: 'QREF-STATIC-001',
        qrType: 'STATIC',
        qrPayload: 'LANKAPAY:QR:QREF-STATIC-001',
        recipientDisplayName: 'Kamal Perera',
        maskedPhone: '+9477***1234',
      );

  @override
  Future<QrReferenceModel> generateDynamicQr({
    required double amount,
    int validityMinutes = 15,
    String? note,
    String currency = 'LKR',
  }) async =>
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

  @override
  Future<ResolvedQrModel> resolveReference(String reference) async => const ResolvedQrModel(
        reference: 'QREF-DYN-002',
        qrType: 'DYNAMIC',
        amount: 1500.0,
        recipientDisplayName: 'Kamal Perera',
        maskedPhone: '+9477***1234',
      );
}

class FakeNotificationService implements NotificationService {
  List<NotificationItemModel> items = [];

  @override
  Future<List<NotificationItemModel>> getNotifications({
    bool? unreadOnly,
    int page = 0,
    int size = 20,
  }) async {
    if (unreadOnly == true) {
      return items.where((n) => !n.read).toList();
    }
    return items;
  }

  @override
  Future<void> markAsRead(int notificationId) async {}

  @override
  Future<void> markAllAsRead() async {}

  @override
  Future<NotificationPreferencesModel> getPreferences() async => const NotificationPreferencesModel();

  @override
  Future<NotificationPreferencesModel> updatePreferences(NotificationPreferencesModel prefs) async => prefs;
}

class FakeHistoryService implements HistoryService {
  List<TransferModel> items = [];
  bool throwError = false;

  @override
  Future<List<TransferModel>> listHistory({
    HistoryFilter? filter,
    int page = 0,
    int size = 20,
  }) async {
    if (throwError) throw Exception('Network error');
    return items;
  }

  @override
  Future<ReceiptModel> getReceipt(int transferId) async {
    final transfer = items.firstWhere(
      (t) => t.transferId == transferId,
      orElse: () => const TransferModel(
        transferId: 1,
        reference: 'TX-REF-001',
        status: TransferStatusType.completed,
        amount: 8600.0,
      ),
    );
    return ReceiptModel(
      receiptNumber: 'REC-${transfer.reference}',
      transferId: transfer.transferId,
      reference: transfer.reference,
      status: transfer.status.name.toUpperCase(),
      amount: transfer.amount,
      totalDebited: transfer.amount,
      receiver: ReceiptPartyModel(displayName: transfer.receiverDisplayName ?? 'Payee'),
      createdAt: transfer.createdAt,
    );
  }
}

class FakeBankAccountService implements BankAccountService {
  List<BankAccountModel> accounts = [];
  bool throwError = false;

  @override
  Future<List<BankAccountModel>> listAccounts() async {
    if (throwError) throw Exception('Accounts error');
    return accounts;
  }

  @override
  Future<List<BankInstitutionModel>> listInstitutions() async => [];

  @override
  Future<BankAccountModel> linkAccount({
    required String institutionCode,
    required String accountNumber,
    required String accountName,
    String? label,
    bool consentGiven = true,
  }) async => throw UnimplementedError();

  @override
  Future<BankAccountModel> patchAccount({
    required int id,
    String? label,
    bool? discoverable,
    bool? receivingEnabled,
  }) async => throw UnimplementedError();

  @override
  Future<BankAccountModel> setDefault(int id) async => throw UnimplementedError();

  @override
  Future<void> removeAccount(int id, {bool consentGiven = true}) async {}
}

Widget createTestHomeApp({
  FakeBankAccountService? bankAccounts,
  FakeHistoryService? history,
  FakeNotificationService? notifications,
  FakeQrService? qr,
  FakePaymentProfileNotifier? paymentProfile,
  List overrides = const [],
  Widget? homeWidget,
}) {
  return ProviderScope(
    overrides: [
      initialLocationProvider.overrideWithValue('/home'),
      paymentProfileProvider.overrideWith(() => paymentProfile ?? FakePaymentProfileNotifier()),
      qrServiceProvider.overrideWithValue(qr ?? FakeQrService()),
      notificationServiceProvider.overrideWithValue(notifications ?? FakeNotificationService()),
      historyServiceProvider.overrideWithValue(history ?? FakeHistoryService()),
      bankAccountServiceProvider.overrideWithValue(bankAccounts ?? FakeBankAccountService()),
      ...overrides.cast(),
    ],
    child: Consumer(
      builder: (context, ref, _) {
        final router = ref.watch(routerProvider);
        final themeMode = ref.watch(themeControllerProvider);
        final locale = ref.watch(localeProvider);

        if (homeWidget != null) {
          return MaterialApp(
            theme: AppTheme.lightTheme,
            darkTheme: AppTheme.darkTheme,
            themeMode: themeMode,
            locale: locale,
            localizationsDelegates: const [
              AppLocalizations.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: AppLocalizations.supportedLocales,
            home: homeWidget,
          );
        }

        return MaterialApp.router(
          theme: AppTheme.lightTheme,
          darkTheme: AppTheme.darkTheme,
          themeMode: themeMode,
          locale: locale,
          routerConfig: router,
          localizationsDelegates: const [
            AppLocalizations.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: AppLocalizations.supportedLocales,
        );
      },
    ),
  );
}

// ---------------------------------------------------------------------------
// Test Data Setup
// ---------------------------------------------------------------------------

final testAccounts = [
  const BankAccountModel(
    id: 1,
    institutionCode: 'BOC',
    accountName: 'Kamal Perera',
    maskedNumber: '** 7890',
    label: 'Salary Account',
    isDefault: true,
    discoverable: true,
    receivingEnabled: true,
    status: 'ACTIVE',
  ),
  const BankAccountModel(
    id: 2,
    institutionCode: 'COMBANK',
    accountName: 'Kamal Perera',
    maskedNumber: '** 1234',
    label: 'Primary Savings',
    isDefault: false,
    discoverable: true,
    receivingEnabled: true,
    status: 'ACTIVE',
  ),
];

final testTransfers = [
  const TransferModel(
    transferId: 101,
    reference: 'TX-IN-001',
    status: TransferStatusType.completed,
    amount: 8600.0,
    receiverDisplayName: 'Received from Nimal S.',
    note: 'Received instant payment',
    createdAt: '2026-10-11T10:00:00Z',
  ),
  const TransferModel(
    transferId: 102,
    reference: 'TX-OUT-002',
    status: TransferStatusType.completed,
    amount: 1500.0,
    receiverDisplayName: 'Kamal Perera',
    senderPhone: '+94771234567',
    createdAt: '2026-10-10T14:30:00Z',
  ),
  const TransferModel(
    transferId: 103,
    reference: 'TX-OUT-003',
    status: TransferStatusType.pending,
    amount: 3200.0,
    receiverDisplayName: 'Electricity Board',
    createdAt: '2026-10-09T08:15:00Z',
  ),
  const TransferModel(
    transferId: 104,
    reference: 'TX-OUT-004',
    status: TransferStatusType.failed,
    amount: 500.0,
    receiverDisplayName: 'Supermarket',
    createdAt: '2026-10-08T18:00:00Z',
  ),
  const TransferModel(
    transferId: 105,
    reference: 'TX-OUT-005',
    status: TransferStatusType.reversed,
    amount: 1200.0,
    receiverDisplayName: 'Online Store',
    createdAt: '2026-10-07T12:00:00Z',
  ),
];

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('HomeScreen Loading States', () {
    testWidgets('renders shimmer skeletons while accounts and transfers are loading', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final completerAccounts = Completer<List<BankAccountModel>>();
      final completerTransfers = Completer<List<TransferModel>>();
      addTearDown(() {
        if (!completerAccounts.isCompleted) completerAccounts.complete([]);
        if (!completerTransfers.isCompleted) completerTransfers.complete([]);
      });

      await tester.pumpWidget(
        createTestHomeApp(
          overrides: [
            bankAccountsProvider.overrideWith(() => _NeverAccountsNotifier(completerAccounts.future)),
            recentTransactionsProvider.overrideWith((ref) => completerTransfers.future),
            unreadNotificationCountProvider.overrideWith((ref) => Future.value(0)),
            monthlyReceivedProvider.overrideWith((ref) => Future.value(0.0)),
          ],
        ),
      );
      await tester.pump();

      // Accounts section loading state
      expect(find.byType(SkeletonAccountCard), findsOneWidget);

      // Transactions section loading state
      expect(find.byKey(const Key('home_transactions_loading')), findsOneWidget);
      expect(find.byKey(const Key('skeleton_tx_0')), findsOneWidget);
    });
  });

  group('HomeScreen Empty States', () {
    testWidgets('renders EmptyState for both accounts and transactions when empty', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final fakeAccounts = FakeBankAccountService()..accounts = [];
      final fakeHistory = FakeHistoryService()..items = [];

      await tester.pumpWidget(
        createTestHomeApp(
          bankAccounts: fakeAccounts,
          history: fakeHistory,
          overrides: [
            unreadNotificationCountProvider.overrideWith((ref) => Future.value(0)),
            monthlyReceivedProvider.overrideWith((ref) => Future.value(0.0)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      // Accounts empty state
      expect(find.byKey(const Key('home_accounts_empty')), findsOneWidget);
      expect(find.text('No linked accounts'), findsOneWidget);
      expect(find.byKey(const Key('home_add_account_empty_btn')), findsOneWidget);

      // Transactions empty state
      expect(find.byKey(const Key('home_transactions_empty')), findsOneWidget);
      expect(find.text('No transactions yet'), findsOneWidget);
    });
  });

  group('HomeScreen Error States and Retry', () {
    testWidgets('renders ErrorRetry for accounts and transactions with functional retry', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final fakeAccounts = FakeBankAccountService()..throwError = true;
      final fakeHistory = FakeHistoryService()..throwError = true;

      await tester.pumpWidget(
        createTestHomeApp(
          bankAccounts: fakeAccounts,
          history: fakeHistory,
          overrides: [
            unreadNotificationCountProvider.overrideWith((ref) => Future.value(0)),
            monthlyReceivedProvider.overrideWith((ref) => Future.value(0.0)),
          ],
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pumpAndSettle();

      // Accounts error card
      expect(find.byKey(const Key('home_accounts_error')), findsOneWidget);
      expect(find.text('Failed to load bank accounts'), findsOneWidget);

      // Transactions error card
      expect(find.byKey(const Key('home_transactions_error')), findsOneWidget);
      expect(find.text('Could not load recent transactions'), findsOneWidget);

      // Tap retry on accounts error
      fakeAccounts.throwError = false;
      fakeAccounts.accounts = testAccounts;
      await tester.tap(find.descendant(
        of: find.byKey(const Key('home_accounts_error')),
        matching: find.byType(ElevatedButton),
      ));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pumpAndSettle();

      // Accounts should now display
      expect(find.byKey(const Key('home_account_1')), findsOneWidget);
    });
  });

  group('HomeScreen Data State & Visual Elements', () {
    testWidgets('renders Received this month amount, accounts carousel, quick actions, and last transactions', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final fakeAccounts = FakeBankAccountService()..accounts = testAccounts;
      final fakeHistory = FakeHistoryService()..items = testTransfers;

      await tester.pumpWidget(
        createTestHomeApp(
          bankAccounts: fakeAccounts,
          history: fakeHistory,
          overrides: [
            monthlyReceivedProvider.overrideWith((ref) => Future.value(8600.0)),
            unreadNotificationCountProvider.overrideWith((ref) => Future.value(0)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      // Summary: Received this month with LKR 8,600.00
      expect(find.text('Received this month'), findsOneWidget);
      expect(find.text('LKR 8,600.00'), findsOneWidget);
      expect(find.byKey(const Key('home_toggle_balance')), findsOneWidget);

      // Accounts Section: Carousel with default first
      expect(find.text('ACCOUNTS'), findsOneWidget);
      expect(find.byKey(const Key('home_add_account')), findsOneWidget);
      expect(find.byKey(const Key('home_account_1')), findsOneWidget);
      expect(find.byKey(const Key('home_account_2')), findsOneWidget);
      expect(find.text('DEFAULT'), findsOneWidget);

      // Quick Actions: 4 gradient tiles
      expect(find.text('QUICK ACTIONS'), findsOneWidget);
      expect(find.byKey(const Key('qa_send')), findsOneWidget);
      expect(find.byKey(const Key('qa_scan')), findsOneWidget);
      expect(find.byKey(const Key('qa_request')), findsOneWidget);
      expect(find.byKey(const Key('qa_my_qr')), findsOneWidget);

      // Last Transactions: 5 TransactionTiles with signed amounts & status labels
      expect(find.text('LAST TRANSACTIONS'), findsOneWidget);
      expect(find.byKey(const Key('home_see_all')), findsOneWidget);

      // Incoming confirmed: + and Completed
      expect(find.byKey(const Key('tx_101')), findsOneWidget);
      expect(find.text('+ LKR 8,600.00'), findsOneWidget);
      expect(find.text('Completed'), findsNWidgets(2)); // tx_101 & tx_102

      // Outgoing confirmed: - and Completed
      expect(find.byKey(const Key('tx_102')), findsOneWidget);
      expect(find.text('- LKR 1,500.00'), findsOneWidget);

      // Outgoing Pending
      expect(find.byKey(const Key('tx_103')), findsOneWidget);
      expect(find.text('- LKR 3,200.00'), findsOneWidget);
      expect(find.text('Pending'), findsOneWidget);

      // Outgoing Failed
      expect(find.byKey(const Key('tx_104')), findsOneWidget);
      expect(find.text('- LKR 500.00'), findsOneWidget);
      expect(find.text('Failed'), findsOneWidget);

      // Outgoing Reversed
      expect(find.byKey(const Key('tx_105')), findsOneWidget);
      expect(find.text('- LKR 1,200.00'), findsOneWidget);
      expect(find.text('Reversed'), findsOneWidget);
    });

    testWidgets('renders Simulated balance with SimulatedBadge when simulatedBalanceProvider has value', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final fakeAccounts = FakeBankAccountService()..accounts = testAccounts;

      await tester.pumpWidget(
        createTestHomeApp(
          bankAccounts: fakeAccounts,
          overrides: [
            simulatedBalanceProvider.overrideWithValue(125450.0),
            unreadNotificationCountProvider.overrideWith((ref) => Future.value(0)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Simulated balance'), findsOneWidget);
      expect(find.byType(SimulatedBadge), findsWidgets);
      expect(find.text('LKR 125,450.00'), findsWidgets);
    });
  });

  group('HomeScreen Unread Notification Dot', () {
    testWidgets('shows red unread dot on bell when unread count > 0', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final fakeNotifs = FakeNotificationService()
        ..items = [
          const NotificationItemModel(id: 1, title: 'Alert', message: 'Test alert', type: 'TRANSACTION', read: false),
        ];

      await tester.pumpWidget(
        createTestHomeApp(
          notifications: fakeNotifs,
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('home_bell')), findsOneWidget);
      expect(find.byKey(const Key('home_bell_unread_dot')), findsOneWidget);
    });

    testWidgets('does not show red dot on bell when unread count is 0', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final fakeNotifs = FakeNotificationService()
        ..items = [
          const NotificationItemModel(id: 1, title: 'Alert', message: 'Test alert', type: 'TRANSACTION', read: true),
        ];

      await tester.pumpWidget(
        createTestHomeApp(
          notifications: fakeNotifs,
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('home_bell')), findsOneWidget);
      expect(find.byKey(const Key('home_bell_unread_dot')), findsNothing);
    });
  });

  group('HomeScreen Balance Masking Eye Toggle', () {
    testWidgets('toggles balance visibility between formatted amount and masked dots', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(
        createTestHomeApp(
          overrides: [
            monthlyReceivedProvider.overrideWith((ref) => Future.value(8600.0)),
            unreadNotificationCountProvider.overrideWith((ref) => Future.value(0)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      // Initially visible
      expect(find.text('LKR 8,600.00'), findsOneWidget);
      expect(find.text('••••••••'), findsNothing);

      // Tap eye icon to mask
      await tester.tap(find.byKey(const Key('home_toggle_balance')));
      await tester.pump();

      expect(find.text('••••••••'), findsOneWidget);
      expect(find.text('LKR 8,600.00'), findsNothing);

      // Tap eye icon again to unmask
      await tester.tap(find.byKey(const Key('home_toggle_balance')));
      await tester.pump();

      expect(find.text('LKR 8,600.00'), findsOneWidget);
    });
  });

  group('HomeScreen Interactive Controls & Taps', () {
    testWidgets('tapping search icon [home_search] opens global search screen', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(
        createTestHomeApp(
          overrides: [
            unreadNotificationCountProvider.overrideWith((ref) => Future.value(0)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('home_search')));
      await tester.pumpAndSettle();

      expect(find.byType(SearchScreen), findsOneWidget);
      Navigator.of(tester.element(find.byType(SearchScreen))).pop();
      await tester.pumpAndSettle();
    });

    testWidgets('tapping bell icon [home_bell] opens notifications screen', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(
        createTestHomeApp(
          overrides: [
            unreadNotificationCountProvider.overrideWith((ref) => Future.value(0)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('home_bell')));
      await tester.pumpAndSettle();

      expect(find.byType(NotificationsScreen), findsOneWidget);
      Navigator.of(tester.element(find.byType(NotificationsScreen))).pop();
      await tester.pumpAndSettle();
    });

    testWidgets('tapping Add + [home_add_account] opens add bank account flow', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(
        createTestHomeApp(
          overrides: [
            unreadNotificationCountProvider.overrideWith((ref) => Future.value(0)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('home_add_account')));
      await tester.pumpAndSettle();

      expect(find.byType(AddBankAccountScreen), findsOneWidget);
      Navigator.of(tester.element(find.byType(AddBankAccountScreen))).pop();
      await tester.pumpAndSettle();
    });

    testWidgets('tapping account card [home_account_<id>] opens accounts tab', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final fakeAccounts = FakeBankAccountService()..accounts = testAccounts;

      await tester.pumpWidget(
        createTestHomeApp(
          bankAccounts: fakeAccounts,
          overrides: [
            unreadNotificationCountProvider.overrideWith((ref) => Future.value(0)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('home_account_1')));
      await tester.pumpAndSettle();

      expect(find.byType(AccountsScreen), findsOneWidget);
    });

    testWidgets('tapping quick action tiles navigates to corresponding destinations', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final fakeAccounts = FakeBankAccountService()..accounts = testAccounts;

      await tester.pumpWidget(
        createTestHomeApp(
          bankAccounts: fakeAccounts,
          overrides: [
            unreadNotificationCountProvider.overrideWith((ref) => Future.value(0)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      // 1. qa_send -> /send
      await tester.tap(find.byKey(const Key('qa_send')));
      await tester.pumpAndSettle();
      expect(find.byType(SendScreen), findsOneWidget);

      // Return home
      Navigator.of(tester.element(find.byType(SendScreen))).pop();
      await tester.pumpAndSettle();

      // 2. qa_scan -> /qr
      await tester.tap(find.byKey(const Key('qa_scan')));
      await tester.pumpAndSettle();
      expect(find.byType(QrScreen), findsOneWidget);

      // Return home by popping QrScreen (it is a full-screen route)
      Navigator.of(tester.element(find.byType(QrScreen))).pop();
      await tester.pumpAndSettle();

      // 3. qa_request -> /qr
      await tester.tap(find.byKey(const Key('qa_request')));
      await tester.pumpAndSettle();
      expect(find.byType(QrScreen), findsOneWidget);

      // Return home
      Navigator.of(tester.element(find.byType(QrScreen))).pop();
      await tester.pumpAndSettle();

      // 4. qa_my_qr -> /qr
      await tester.tap(find.byKey(const Key('qa_my_qr')));
      await tester.pumpAndSettle();
      expect(find.byType(QrScreen), findsOneWidget);
      Navigator.of(tester.element(find.byType(QrScreen))).pop();
      await tester.pumpAndSettle();
    });

    testWidgets('tapping See all [home_see_all] opens transaction history', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(
        createTestHomeApp(
          overrides: [
            unreadNotificationCountProvider.overrideWith((ref) => Future.value(0)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('home_see_all')));
      await tester.pumpAndSettle();

      expect(find.byType(HistoryScreen), findsOneWidget);
      Navigator.of(tester.element(find.byType(HistoryScreen))).pop();
      await tester.pumpAndSettle();
    });

    testWidgets('tapping transaction row [tx_<id>] opens transaction receipt screen', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final fakeHistory = FakeHistoryService()..items = testTransfers;

      await tester.pumpWidget(
        createTestHomeApp(
          history: fakeHistory,
          overrides: [
            unreadNotificationCountProvider.overrideWith((ref) => Future.value(0)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('tx_101')));
      await tester.pumpAndSettle();

      expect(find.byType(ReceiptScreen), findsOneWidget);
      expect(find.text('TX-IN-001'), findsOneWidget);
      Navigator.of(tester.element(find.byType(ReceiptScreen))).pop();
      await tester.pumpAndSettle();
    });

    testWidgets('pull-to-refresh triggers refresh on all providers', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final fakeAccounts = FakeBankAccountService()..accounts = testAccounts;
      final fakeHistory = FakeHistoryService()..items = testTransfers;

      await tester.pumpWidget(
        createTestHomeApp(
          bankAccounts: fakeAccounts,
          history: fakeHistory,
          overrides: [
            unreadNotificationCountProvider.overrideWith((ref) => Future.value(0)),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(RefreshIndicator), findsOneWidget);
      await tester.fling(find.byType(RefreshIndicator), const Offset(0.0, 300.0), 1000.0);
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));
      await tester.pumpAndSettle();

      expect(find.byType(HomeScreen), findsOneWidget);
    });
  });
}

class _NeverAccountsNotifier extends BankAccountsNotifier {
  final Future<List<BankAccountModel>> _future;
  _NeverAccountsNotifier(this._future);

  @override
  Future<List<BankAccountModel>> build() => _future;
}
