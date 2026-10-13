import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lankapay_id/core/localization/locale_controller.dart';
import 'package:lankapay_id/core/routing/app_router.dart';
import 'package:lankapay_id/core/test_keys.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/core/theme/theme_controller.dart';
import 'package:lankapay_id/features/accounts/data/bank_account_service.dart';
import 'package:lankapay_id/features/accounts/domain/bank_account_model.dart';
import 'package:lankapay_id/features/accounts/presentation/accounts_screen.dart';
import 'package:lankapay_id/features/history/data/history_service.dart';
import 'package:lankapay_id/features/notifications/data/notification_service.dart';
import 'package:lankapay_id/features/notifications/domain/notification_models.dart';
import 'package:lankapay_id/features/send/presentation/send_screen.dart';
import 'package:lankapay_id/features/qr/data/qr_service.dart';
import 'package:lankapay_id/features/qr/domain/qr_models.dart';
import 'package:lankapay_id/features/send/data/transfer_service.dart';
import 'package:lankapay_id/features/send/domain/transfer_models.dart';
import 'package:lankapay_id/features/auth/data/auth_service.dart';
import 'package:lankapay_id/features/auth/domain/auth_state.dart';
import 'package:dio/dio.dart';
import 'package:lankapay_id/features/cards/data/card_service.dart';
import 'package:lankapay_id/features/cards/domain/card_model.dart';
import 'package:lankapay_id/features/settings/presentation/privacy_settings_screen.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';

// ---------------------------------------------------------------------------
// Mock Services
// ---------------------------------------------------------------------------
class FakeAllKeysBankAccountService implements BankAccountService {
  List<BankAccountModel> accounts = [
    const BankAccountModel(
      id: 1,
      institutionCode: 'BOC',
      accountName: 'Kamal Perera',
      maskedNumber: '******7890',
      label: 'Salary Account',
      isDefault: true,
      discoverable: true,
      receivingEnabled: true,
      status: 'ACTIVE',
    ),
    const BankAccountModel(
      id: 2,
      institutionCode: 'COMB',
      accountName: 'Kamal Perera',
      maskedNumber: '******4321',
      label: 'Savings Account',
      isDefault: false,
      discoverable: false,
      receivingEnabled: false,
      status: 'ACTIVE',
    ),
  ];

  @override
  Future<List<BankAccountModel>> listAccounts() async => List.from(accounts);

  @override
  Future<List<BankInstitutionModel>> listInstitutions() async => [
        const BankInstitutionModel(institutionCode: 'BOC', displayName: 'Bank of Ceylon'),
        const BankInstitutionModel(institutionCode: 'COMB', displayName: 'Commercial Bank'),
      ];

  @override
  Future<BankAccountModel> linkAccount({
    required String institutionCode,
    required String accountNumber,
    required String accountName,
    String? label,
    bool consentGiven = true,
  }) async {
    final newAccount = BankAccountModel(
      id: 3,
      institutionCode: institutionCode,
      accountName: accountName,
      maskedNumber: '******${accountNumber.substring(accountNumber.length - 4)}',
      label: label,
      isDefault: false,
      discoverable: true,
      receivingEnabled: true,
      status: 'ACTIVE',
    );
    accounts.add(newAccount);
    return newAccount;
  }

  @override
  Future<BankAccountModel> patchAccount({
    required int id,
    String? label,
    bool? discoverable,
    bool? receivingEnabled,
  }) async {
    final idx = accounts.indexWhere((a) => a.id == id);
    if (idx != -1) {
      accounts[idx] = accounts[idx].copyWith(
        label: label,
        discoverable: discoverable,
        receivingEnabled: receivingEnabled,
      );
      return accounts[idx];
    }
    throw Exception('Not found');
  }

  @override
  Future<BankAccountModel> setDefault(int id) async {
    for (int i = 0; i < accounts.length; i++) {
      accounts[i] = accounts[i].copyWith(isDefault: accounts[i].id == id);
    }
    return accounts.firstWhere((a) => a.id == id);
  }

  @override
  Future<void> removeAccount(int id, {bool consentGiven = true}) async {
    accounts.removeWhere((a) => a.id == id);
  }
}

class FakeAllKeysHistoryService implements HistoryService {
  final List<TransferModel> transfers = [
    const TransferModel(
      transferId: 101,
      reference: 'TX-REF-101',
      status: TransferStatusType.completed,
      amount: 4200.0,
      receiverDisplayName: 'Supermarket POS',
      senderPhone: '0771234567',
      createdAt: '2026-10-10T14:30:00Z',
    ),
  ];

  @override
  Future<List<TransferModel>> listHistory({
    int page = 0,
    int size = 20,
    HistoryFilter? filter,
  }) async {
    return List.from(transfers);
  }

  @override
  Future<ReceiptModel> getReceipt(int transferId) async {
    return const ReceiptModel(
      receiptNumber: 'REC-TX-REF-101',
      transferId: 101,
      reference: 'TX-REF-101',
      status: 'COMPLETED',
      amount: 4200.0,
      totalDebited: 4200.0,
      receiver: ReceiptPartyModel(displayName: 'Supermarket POS'),
      note: 'Groceries',
      createdAt: '2026-10-10T14:30:00Z',
      simulated: true,
      digitalSignature: 'SIG-LANKAPAY-VERIFIED-101',
    );
  }
}

class FakeAllKeysTransferService implements TransferService {
  @override
  Future<LookupResultModel> lookupRecipient(String phone) async {
    return const LookupResultModel(
      resultType: 'FOUND',
      displayName: 'Kamal Perera',
      verificationStatus: 'VERIFIED',
      accounts: [
        EligibleAccountModel(
          institutionCode: 'BOC',
          bankDisplayName: 'Bank of Ceylon',
          maskedNumber: '****7890',
          isDefault: true,
        ),
      ],
    );
  }

  @override
  Future<QuoteModel> getQuote({
    int? payeeId,
    String? payeePhone,
    required double amount,
    String currency = 'LKR',
    FundingSourceModel? fundingSource,
  }) async {
    return QuoteModel(
      quoteReference: 'QUO-KEYSTEST-123',
      payeeId: 42,
      payeeDisplayName: 'Kamal Perera',
      amount: amount,
      fee: 0.0,
      totalAmount: amount,
      currency: currency,
      expiresAt: '2026-10-12T12:00:00Z',
    );
  }

  @override
  Future<TransferModel> initiateTransfer({
    required String idempotencyKey,
    required String pin,
    int? payeeId,
    String? payeePhone,
    required double amount,
    String currency = 'LKR',
    String? quoteReference,
    String? note,
    FundingSourceModel? fundingSource,
  }) async {
    return const TransferModel(
      transferId: 999,
      reference: 'TX-KEYSTEST-999',
      status: TransferStatusType.completed,
      amount: 500.0,
      receiverDisplayName: 'Kamal Perera',
    );
  }

  @override
  Future<TransferModel> challengeTransfer({
    required int transferId,
    required String otp,
  }) async {
    return const TransferModel(
      transferId: 999,
      reference: 'TX-KEYSTEST-CHALLENGED-999',
      status: TransferStatusType.completed,
      amount: 500.0,
      receiverDisplayName: 'Kamal Perera',
    );
  }

  @override
  Future<TransferModel> getTransferById(int transferId) async {
    return const TransferModel(
      transferId: 999,
      reference: 'TX-KEYSTEST-999',
      status: TransferStatusType.completed,
      amount: 500.0,
      receiverDisplayName: 'Kamal Perera',
    );
  }
}

class FakeAllKeysNotificationService implements NotificationService {
  @override
  Future<List<NotificationItemModel>> getNotifications({
    bool? unreadOnly,
    int page = 0,
    int size = 20,
  }) async =>
      [
        const NotificationItemModel(
          id: 1,
          title: 'Payment Alert',
          message: 'Simulated payment of LKR 4,200.00 confirmed.',
          type: 'TRANSACTION',
          read: false,
          createdAt: '2026-10-10T14:30:00Z',
        ),
      ];

  @override
  Future<void> markAsRead(int notificationId) async {}

  @override
  Future<void> markAllAsRead() async {}

  @override
  Future<NotificationPreferencesModel> getPreferences() async =>
      const NotificationPreferencesModel();

  @override
  Future<NotificationPreferencesModel> updatePreferences(
          NotificationPreferencesModel p) async =>
      p;
}

class FakeAllKeysQrService implements QrService {
  @override
  Future<QrReferenceModel> generateStaticQr() async {
    return const QrReferenceModel(
      reference: 'QREF-STATIC-001',
      qrType: 'STATIC',
      qrPayload: 'LANKAPAY:STATIC:USER_42',
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
    return QrReferenceModel(
      reference: 'QREF-DYN-002',
      qrType: 'DYNAMIC',
      qrPayload: 'LANKAPAY:DYNAMIC:USER_42:$amount',
      amount: amount,
      currency: currency,
      note: note,
      expiresAt: DateTime.now().add(Duration(minutes: validityMinutes)).toUtc().toIso8601String(),
      oneTimeUse: true,
    );
  }

  @override
  Future<ResolvedQrModel> resolveReference(String reference) async {
    return const ResolvedQrModel(
      reference: 'QREF-DYN-002',
      qrType: 'DYNAMIC',
      recipientDisplayName: 'Kamal Perera',
      maskedPhone: '+9471***8888',
      amount: 1250.0,
      currency: 'LKR',
    );
  }
}

class FixedThemeController extends ThemeController {
  final ThemeMode fixedMode;
  FixedThemeController(this.fixedMode);

  @override
  ThemeMode build() => fixedMode;
}

class FakeAllKeysPaymentProfileNotifier extends PaymentProfileNotifier {
  @override
  PaymentProfileState build() {
    return const PaymentProfileState(
      safeName: 'Kamal Perera',
      isDiscoverable: true,
      isLoading: false,
    );
  }
}

class FakeAllKeysAuthNotifier extends AuthNotifier {
  @override
  AuthState build() {
    return const AuthAuthenticated(
      accessToken: 'test-token',
      userId: '1',
      phone: '+94771234567',
    );
  }
}

class FakeAllKeysCardService implements CardService {
  @override
  Dio get dio => Dio();
  @override
  Dio get vaultDio => Dio();

  List<CardModel> cards = [
    const CardModel(
      id: 101,
      userId: 1,
      cardKind: CardKind.virtual,
      status: CardStatus.active,
      last4: '1234',
      expMonth: 12,
      expYear: 2028,
      nickname: 'Virtual Daily',
      perPaymentLimitMinor: 2500000,
      dailyLimitMinor: 10000000,
      onlinePaymentsEnabled: true,
      brandLabel: 'TEST CARD',
      linkedBankAccountId: 1,
    ),
  ];

  @override
  Future<TokenizeResultModel> tokenize({
    required String pan,
    required String expMonth,
    required String expYear,
    required String cvv,
    required String holderName,
  }) async =>
      const TokenizeResultModel(
        cardToken: 'tok_test_allkeys',
        last4: '1234',
        expMonth: '12',
        expYear: '28',
        brandLabel: 'TEST CARD',
      );

  @override
  Future<List<CardModel>> listCards() async => List.from(cards);

  @override
  Future<CardModel> getCard(int id) async => cards.firstWhere((c) => c.id == id);

  @override
  Future<CardModel> linkCard({
    required String cardToken,
    String? nickname,
    int? linkedBankAccountId,
    bool consentGiven = true,
    String? pin,
  }) async => cards.first;

  @override
  Future<CardModel> issueVirtualCard({
    required int linkedBankAccountId,
    String? nickname,
    String? pin,
  }) async => cards.first;

  @override
  Future<CardModel> verifyCard({
    required int id,
    required String otp,
  }) async => cards.first;

  @override
  Future<CardModel> freezeCard(int id) async => cards.first;

  @override
  Future<CardModel> unfreezeCard(int id, {String? pin}) async => cards.first;

  @override
  Future<CardRevealModel> revealCard(int id, {String? pin}) async =>
      const CardRevealModel(
        cardId: 101,
        last4: '1234',
        pan: '990000000000' '0010',
        expMonth: '12',
        expYear: '28',
        cvv: '123',
        brandLabel: 'TEST CARD',
      );

  @override
  Future<CardModel> patchCard(
    int id, {
    String? nickname,
    int? perPaymentLimitMinor,
    int? dailyLimitMinor,
    bool? onlinePaymentsEnabled,
    int? linkedBankAccountId,
    String? pin,
  }) async => cards.first;

  @override
  Future<void> removeCard(int id, {String? pin}) async {}
}

Widget createE2ETestApp({
  String initialLocation = '/home',
  FakeAllKeysBankAccountService? bankService,
  FakeAllKeysHistoryService? historyService,
  FakeAllKeysTransferService? transferService,
  FakeAllKeysNotificationService? notifService,
  FakeAllKeysQrService? qrService,
  FakeAllKeysCardService? cardService,
  ThemeMode? fixedThemeMode,
}) {
  return ProviderScope(
    overrides: [
      initialLocationProvider.overrideWithValue(initialLocation),
      authStateProvider.overrideWith(() => FakeAllKeysAuthNotifier()),
      paymentProfileProvider
          .overrideWith(() => FakeAllKeysPaymentProfileNotifier()),
      if (fixedThemeMode != null)
        themeControllerProvider.overrideWith(() => FixedThemeController(fixedThemeMode)),
      bankAccountServiceProvider
          .overrideWithValue(bankService ?? FakeAllKeysBankAccountService()),
      cardServiceProvider
          .overrideWithValue(cardService ?? FakeAllKeysCardService()),
      historyServiceProvider
          .overrideWithValue(historyService ?? FakeAllKeysHistoryService()),
      transferServiceProvider
          .overrideWithValue(transferService ?? FakeAllKeysTransferService()),
      notificationServiceProvider
          .overrideWithValue(notifService ?? FakeAllKeysNotificationService()),
      qrServiceProvider.overrideWithValue(qrService ?? FakeAllKeysQrService()),
    ],
    child: Consumer(
      builder: (context, ref, _) {
        final router = ref.watch(routerProvider);
        final themeMode = ref.watch(themeControllerProvider);
        final locale = ref.watch(localeProvider);

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

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('Comprehensive Key Tapping E2E Navigation & Control Tests', () {
    testWidgets('Tapping all keys on Home screen triggers navigation, dialogs, and balance toggles',
        (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createE2ETestApp(initialLocation: '/home'));
      await tester.pumpAndSettle();

      // 1. AppTestKeys.navHome
      expect(find.byKey(AppTestKeys.navHome), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.navHome));
      await tester.pumpAndSettle();

      // 2. AppTestKeys.homeToggleBalance
      expect(find.byKey(AppTestKeys.homeToggleBalance), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.homeToggleBalance));
      await tester.pumpAndSettle();
      expect(find.text('••••••••'), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.homeToggleBalance));
      await tester.pumpAndSettle();
      expect(find.text('••••••••'), findsNothing);

      // 3. AppTestKeys.homeSearch -> navigates to /send
      expect(find.byKey(AppTestKeys.homeSearch), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.homeSearch));
      await tester.pumpAndSettle();
      // Back to Home
      final searchBack = find.byKey(const Key('search_back_btn'));
      if (searchBack.evaluate().isNotEmpty) {
        await tester.tap(searchBack);
      } else {
        Navigator.of(tester.element(find.byKey(const Key('search_screen')))).pop();
      }
      await tester.pumpAndSettle();

      // 4. AppTestKeys.homeBell -> navigates to /notifications
      expect(find.byKey(AppTestKeys.homeBell), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.homeBell));
      await tester.pumpAndSettle();
      expect(find.text('Notifications'), findsOneWidget);
      // Pop back
      final backFinder = find.byTooltip('Back');
      if (backFinder.evaluate().isNotEmpty) {
        await tester.tap(backFinder.first);
        await tester.pumpAndSettle();
      } else {
        await tester.tap(find.byKey(AppTestKeys.navHome));
        await tester.pumpAndSettle();
      }

      // 5. AppTestKeys.homeAddAccount -> navigates to /accounts/add
      expect(find.byKey(AppTestKeys.homeAddAccount), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.homeAddAccount));
      await tester.pumpAndSettle();
      expect(find.text('Link Bank Account'), findsOneWidget);
      // Pop back
      if (find.byTooltip('Back').evaluate().isNotEmpty) {
        await tester.tap(find.byTooltip('Back').first);
        await tester.pumpAndSettle();
      }

      // 6. AppTestKeys.homeAccount(1) -> opens Accounts tab
      final accountCardKey = AppTestKeys.homeAccount(1);
      expect(find.byKey(accountCardKey), findsOneWidget);
      await tester.tap(find.byKey(accountCardKey));
      await tester.pumpAndSettle();
      expect(find.byKey(AppTestKeys.accountsAdd), findsOneWidget);
      // Return to Home
      if (find.byTooltip('Back').evaluate().isNotEmpty) {
        await tester.tap(find.byTooltip('Back').first);
      } else {
        Navigator.of(tester.element(find.byType(AccountsScreen))).pop();
      }
      await tester.pumpAndSettle();

      // 7. Quick Actions: qaSend, qaScan, qaRequest, qaMyQr
      expect(find.byKey(AppTestKeys.qaSend), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.qaSend));
      await tester.pumpAndSettle();
      if (find.byTooltip('Back').evaluate().isNotEmpty) {
        await tester.tap(find.byTooltip('Back').first);
      } else {
        Navigator.of(tester.element(find.byType(SendScreen))).pop();
      }
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.qaScan), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.qaScan));
      await tester.pumpAndSettle();
      expect(find.text('LANKAQR'), findsOneWidget);
      if (find.byTooltip('Back').evaluate().isNotEmpty) {
        await tester.tap(find.byTooltip('Back').first);
        await tester.pumpAndSettle();
      }

      expect(find.byKey(AppTestKeys.qaRequest), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.qaRequest));
      await tester.pumpAndSettle();
      expect(find.text('LANKAQR'), findsOneWidget);
      if (find.byTooltip('Back').evaluate().isNotEmpty) {
        await tester.tap(find.byTooltip('Back').first);
        await tester.pumpAndSettle();
      }

      // 8. AppTestKeys.homeSeeAll -> opens History
      expect(find.byKey(AppTestKeys.homeSeeAll), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.homeSeeAll));
      await tester.pumpAndSettle();
      expect(find.text('History'), findsOneWidget);
      if (find.byTooltip('Back').evaluate().isNotEmpty) {
        await tester.tap(find.byTooltip('Back').first);
        await tester.pumpAndSettle();
      }

      // 9. AppTestKeys.tx(101) -> opens Receipt
      final txKey = AppTestKeys.tx(101);
      expect(find.byKey(txKey), findsOneWidget);
      await tester.tap(find.byKey(txKey));
      await tester.pumpAndSettle();
      expect(find.text('Receipt'), findsOneWidget);
      if (find.byTooltip('Back').evaluate().isNotEmpty) {
        await tester.tap(find.byTooltip('Back').first);
        await tester.pumpAndSettle();
      }
    });

    testWidgets('Tapping all keys on Accounts screen triggers settings, freeze, rename, and add',
        (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final bankService = FakeAllKeysBankAccountService();
      await tester.pumpWidget(createE2ETestApp(
        initialLocation: '/accounts',
        bankService: bankService,
      ));
      await tester.pumpAndSettle();

      // 2. AppTestKeys.accountToggleVisibility (masks/unmasks digits)
      expect(find.byKey(AppTestKeys.accountToggleVisibility), findsWidgets);
      await tester.tap(find.byKey(AppTestKeys.accountToggleVisibility).first);
      await tester.pumpAndSettle();
      expect(find.textContaining('7890'), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.accountToggleVisibility).first);
      await tester.pumpAndSettle();
      expect(find.textContaining('••••'), findsWidgets);

      // 3. AppTestKeys.accountsAdd -> opens /accounts/add
      expect(find.byKey(AppTestKeys.accountsAdd), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.accountsAdd));
      await tester.pumpAndSettle();
      expect(find.text('Link Bank Account'), findsOneWidget);
      if (find.byTooltip('Back').evaluate().isNotEmpty) {
        await tester.tap(find.byTooltip('Back').first);
        await tester.pumpAndSettle();
      }

      // 4. AppTestKeys.btnAddAccount (dock button)
      expect(find.byKey(AppTestKeys.btnAddAccount), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.btnAddAccount));
      await tester.pumpAndSettle();
      expect(find.text('Link Bank Account'), findsOneWidget);
      if (find.byTooltip('Back').evaluate().isNotEmpty) {
        await tester.tap(find.byTooltip('Back').first);
        await tester.pumpAndSettle();
      }

      // 5. AppTestKeys.btnFreeze -> shows freeze confirmation dialog
      expect(find.byKey(AppTestKeys.btnFreeze), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.btnFreeze));
      await tester.pumpAndSettle();
      expect(find.byKey(AppTestKeys.confirmPauseBtn), findsOneWidget);
      // Tap cancel
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();

      // 6. AppTestKeys.btnAccountSettings -> opens bottom sheet
      expect(find.byKey(AppTestKeys.btnAccountSettings), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.btnAccountSettings));
      await tester.pumpAndSettle();

      // 7. Settings sheet toggles: discoverable, receiving, setDefault, rename, remove
      expect(find.byKey(AppTestKeys.settingsToggleDiscoverable), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.settingsToggleDiscoverable));
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.settingsToggleReceiving), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.settingsToggleReceiving));
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.settingsRenameBtn), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.settingsRenameBtn));
      await tester.pumpAndSettle();
      expect(find.byKey(AppTestKeys.renameAccountSaveBtn), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.renameAccountSaveBtn));
      await tester.pumpAndSettle();

      // Close bottom sheet if open
      if (find.byKey(AppTestKeys.btnAccountSettings).evaluate().isEmpty) {
        Navigator.of(tester.element(find.byType(Scaffold).first)).pop();
        await tester.pumpAndSettle();
      }
    });

    testWidgets('Tapping all keys on Send screen triggers search, scan, and transfer steps',
        (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createE2ETestApp(initialLocation: '/send'));
      await tester.pumpAndSettle();

      // 2. AppTestKeys.sendScanQr -> opens scanner
      expect(find.byKey(AppTestKeys.sendScanQr), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.sendScanQr));
      await tester.pumpAndSettle();
      if (find.byTooltip('Back').evaluate().isNotEmpty) {
        await tester.tap(find.byTooltip('Back').first);
        await tester.pumpAndSettle();
      }

      // 3. AppTestKeys.sendNew -> activates search
      expect(find.byKey(AppTestKeys.sendNew), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.sendNew));
      await tester.pumpAndSettle();

      // 4. Quick Menu Tiles: tilePayByPhone, tileScanQr, tileRequests, tileBetweenAccounts
      expect(find.byKey(AppTestKeys.tilePayByPhone), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.tilePayByPhone));
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.tileScanQr), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.tileScanQr));
      await tester.pumpAndSettle();
      if (find.byTooltip('Back').evaluate().isNotEmpty) {
        await tester.tap(find.byTooltip('Back').first);
        await tester.pumpAndSettle();
      }

      expect(find.byKey(AppTestKeys.tileRequests), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.tileRequests));
      await tester.pumpAndSettle();
      if (find.byTooltip('Back').evaluate().isNotEmpty) {
        await tester.tap(find.byTooltip('Back').first);
        await tester.pumpAndSettle();
      }

      expect(find.byKey(AppTestKeys.tileBetweenAccounts), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.tileBetweenAccounts));
      await tester.pumpAndSettle();
      expect(find.text('Between Accounts'), findsOneWidget);
      await tester.tap(find.text('OK'));
      await tester.pumpAndSettle();

      // 5. Transfer search input: sendPhoneField, sendLookupBtn
      expect(find.byKey(AppTestKeys.sendPhoneField), findsOneWidget);
      await tester.enterText(find.byKey(AppTestKeys.sendPhoneField), '0771234567');
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.sendLookupBtn), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.sendLookupBtn));
      await tester.pumpAndSettle();

      // Verified recipient flow: sendAmountField, sendNoteField, sendReviewBtn
      expect(find.byKey(AppTestKeys.sendAmountField), findsOneWidget);
      await tester.enterText(find.byKey(AppTestKeys.sendAmountField), '500.00');
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.sendNoteField), findsOneWidget);
      await tester.enterText(find.byKey(AppTestKeys.sendNoteField), 'Lunch payment');
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.sendReviewBtn), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.sendReviewBtn));
      await tester.pumpAndSettle();
      if (Navigator.of(tester.element(find.byKey(AppTestKeys.sendReviewBtn))).canPop()) {
        Navigator.of(tester.element(find.byKey(AppTestKeys.sendReviewBtn))).pop();
        await tester.pumpAndSettle();
      }
    });

    testWidgets('Tapping all keys on More screen triggers sub-modals, theme selection, and logout dialog',
        (WidgetTester tester) async {
      tester.view.physicalSize = const Size(1080, 2400);
      tester.view.devicePixelRatio = 2.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createE2ETestApp(initialLocation: '/more'));
      await tester.pumpAndSettle();

      // 2. AppTestKeys.moreProfile -> opens profile sheet, closes via profileCloseBtn
      expect(find.byKey(AppTestKeys.moreProfile), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.moreProfile));
      await tester.pumpAndSettle();
      expect(find.byKey(AppTestKeys.profileCloseBtn), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.profileCloseBtn));
      await tester.pumpAndSettle();

      // 3. AppTestKeys.moreQr -> opens /qr
      expect(find.byKey(AppTestKeys.moreQr), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.moreQr));
      await tester.pumpAndSettle();
      expect(find.text('LANKAQR'), findsOneWidget);
      if (find.byTooltip('Back').evaluate().isNotEmpty) {
        await tester.tap(find.byTooltip('Back').first);
        await tester.pumpAndSettle();
      }

      // 4. AppTestKeys.moreRequests -> opens /qr
      expect(find.byKey(AppTestKeys.moreRequests), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.moreRequests));
      await tester.pumpAndSettle();
      expect(find.text('LANKAQR'), findsOneWidget);
      if (find.byTooltip('Back').evaluate().isNotEmpty) {
        await tester.tap(find.byTooltip('Back').first);
        await tester.pumpAndSettle();
      }

      // 5. AppTestKeys.moreHistory -> opens /history
      expect(find.byKey(AppTestKeys.moreHistory), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.moreHistory));
      await tester.pumpAndSettle();
      expect(find.text('History'), findsOneWidget);
      if (find.byTooltip('Back').evaluate().isNotEmpty) {
        await tester.tap(find.byTooltip('Back').first);
        await tester.pumpAndSettle();
      }

      // 6. AppTestKeys.moreNotifications -> opens /notifications
      expect(find.byKey(AppTestKeys.moreNotifications), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.moreNotifications));
      await tester.pumpAndSettle();
      expect(find.text('Notifications'), findsOneWidget);
      if (find.byTooltip('Back').evaluate().isNotEmpty) {
        await tester.tap(find.byTooltip('Back').first);
        await tester.pumpAndSettle();
      }

      // 7. AppTestKeys.morePrivacy -> opens /settings/privacy
      expect(find.byKey(AppTestKeys.morePrivacy), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.morePrivacy));
      await tester.pumpAndSettle();
      expect(find.byKey(AppTestKeys.privacyDiscoverableSwitch), findsOneWidget);
      if (find.byTooltip('Back').evaluate().isNotEmpty) {
        await tester.tap(find.byTooltip('Back').first);
        await tester.pumpAndSettle();
      }

      // 8. AppTestKeys.moreSecurity -> opens /settings/security
      expect(find.byKey(AppTestKeys.moreSecurity), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.moreSecurity));
      await tester.pumpAndSettle();
      expect(find.byKey(AppTestKeys.securityBiometricsSwitch), findsOneWidget);
      if (find.byTooltip('Back').evaluate().isNotEmpty) {
        await tester.tap(find.byTooltip('Back').first);
        await tester.pumpAndSettle();
      }

      // 9. AppTestKeys.moreLanguage -> opens language modal
      expect(find.byKey(AppTestKeys.moreLanguage), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.moreLanguage));
      await tester.pumpAndSettle();
      expect(find.textContaining('Select Language'), findsOneWidget);
      // Select English and dismiss
      await tester.tap(find.textContaining('English'));
      await tester.pumpAndSettle();

      // 10. AppTestKeys.moreHelp -> opens help sheet, closes with helpCloseBtn
      expect(find.byKey(AppTestKeys.moreHelp), findsOneWidget);
      await tester.ensureVisible(find.byKey(AppTestKeys.moreHelp));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(AppTestKeys.moreHelp));
      await tester.pumpAndSettle();
      expect(find.byKey(AppTestKeys.helpCloseBtn), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.helpCloseBtn));
      await tester.pumpAndSettle();

      // 11. Theme Options: themeOptionLight, themeOptionDark, themeOptionSystem
      await tester.ensureVisible(find.byKey(AppTestKeys.themeOptionLight));
      await tester.pumpAndSettle();
      expect(find.byKey(AppTestKeys.themeOptionLight), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.themeOptionLight));
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.themeOptionDark), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.themeOptionDark));
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.themeOptionSystem), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.themeOptionSystem));
      await tester.pumpAndSettle();

      // 12. AppTestKeys.moreLogout -> opens dialog with moreLogoutConfirmBtn
      await tester.ensureVisible(find.byKey(AppTestKeys.moreLogout));
      await tester.pumpAndSettle();
      expect(find.byKey(AppTestKeys.moreLogout), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.moreLogout));
      await tester.pumpAndSettle();
      expect(find.byKey(AppTestKeys.moreLogoutConfirmBtn), findsOneWidget);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
    });
  });
}
