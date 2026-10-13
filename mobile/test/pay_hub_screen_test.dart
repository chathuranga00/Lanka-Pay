import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:lankapay_id/core/config/feature_flags.dart';
import 'package:lankapay_id/core/test_keys.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/features/accounts/data/bank_account_service.dart';
import 'package:lankapay_id/features/accounts/domain/bank_account_model.dart';
import 'package:lankapay_id/features/cards/data/card_service.dart';
import 'package:lankapay_id/features/cards/domain/card_model.dart';
import 'package:lankapay_id/features/home/data/home_providers.dart';
import 'package:lankapay_id/features/pay/presentation/pay_hub_screen.dart';
import 'package:lankapay_id/features/send/data/transfer_service.dart';
import 'package:lankapay_id/features/send/domain/transfer_models.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';

// -----------------------------------------------------------------------------
// Fakes & Stubs
// -----------------------------------------------------------------------------

class FakeTransferService extends TransferService {
  final Future<LookupResultModel> Function(String phone)? onLookup;

  FakeTransferService({this.onLookup}) : super(Dio());

  @override
  Future<LookupResultModel> lookupRecipient(String phone) async {
    if (onLookup != null) {
      return onLookup!(phone);
    }
    return const LookupResultModel(
      resultType: 'FOUND',
      displayName: 'Kamal Perera',
      verificationStatus: 'VERIFIED',
      accounts: [
        EligibleAccountModel(
          institutionCode: 'BOC',
          bankDisplayName: 'Bank of Ceylon',
          maskedNumber: '****1234',
          isDefault: true,
        ),
      ],
    );
  }
}

class FakeBankAccountService implements BankAccountService {
  final List<BankAccountModel> accounts;

  FakeBankAccountService({List<BankAccountModel>? accounts})
      : accounts = accounts ??
            const [
              BankAccountModel(
                id: 1,
                institutionCode: 'BOC',
                accountName: 'Kamal Perera',
                maskedNumber: '******7890',
                label: 'Primary Account',
                isDefault: true,
                discoverable: true,
                receivingEnabled: true,
                status: 'ACTIVE',
              ),
              BankAccountModel(
                id: 2,
                institutionCode: 'COMB',
                accountName: 'Kamal Perera',
                maskedNumber: '******4321',
                label: 'Savings Account',
                isDefault: false,
                discoverable: false,
                receivingEnabled: true,
                status: 'ACTIVE',
              ),
            ];

  @override
  Future<List<BankAccountModel>> listAccounts() async => accounts;
  @override
  Future<List<BankInstitutionModel>> listInstitutions() async => [];
  @override
  Future<BankAccountModel> linkAccount({
    required String institutionCode,
    required String accountNumber,
    required String accountName,
    String? label,
    bool consentGiven = true,
  }) async =>
      throw UnimplementedError();
  @override
  Future<BankAccountModel> patchAccount({
    required int id,
    String? label,
    bool? discoverable,
    bool? receivingEnabled,
  }) async =>
      throw UnimplementedError();
  @override
  Future<BankAccountModel> setDefault(int id) async => throw UnimplementedError();
  @override
  Future<void> removeAccount(int id, {bool consentGiven = true}) async {}
}

class FakeCardService implements CardService {
  @override
  final Dio dio = Dio();
  @override
  final Dio vaultDio = Dio();

  final List<CardModel> cards;

  FakeCardService({List<CardModel>? cards})
      : cards = cards ??
            const [
              CardModel(
                id: 101,
                userId: 1,
                cardKind: CardKind.virtual,
                status: CardStatus.active,
                last4: '0010',
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
      throw UnimplementedError();
  @override
  Future<List<CardModel>> listCards() async => cards;
  @override
  Future<CardModel> getCard(int id) async => cards.firstWhere((c) => c.id == id);
  @override
  Future<CardModel> linkCard({
    required String cardToken,
    String? nickname,
    int? linkedBankAccountId,
    bool consentGiven = true,
    String? pin,
  }) async =>
      throw UnimplementedError();
  @override
  Future<CardModel> issueVirtualCard({
    required int linkedBankAccountId,
    String? nickname,
    String? pin,
  }) async =>
      throw UnimplementedError();
  @override
  Future<CardModel> patchCard(
    int id, {
    String? nickname,
    int? perPaymentLimitMinor,
    int? dailyLimitMinor,
    bool? onlinePaymentsEnabled,
    String? pin,
  }) async =>
      throw UnimplementedError();
  @override
  Future<CardModel> verifyCard({required int id, required String otp}) async =>
      throw UnimplementedError();
  @override
  Future<CardModel> freezeCard(int id) async => throw UnimplementedError();
  @override
  Future<CardModel> unfreezeCard(int id, {String? pin}) async => throw UnimplementedError();
  @override
  Future<CardRevealModel> revealCard(int id, {String? pin}) async => throw UnimplementedError();
  @override
  Future<void> removeCard(int id, {String? pin}) async {}
}

List<TransferModel> createMockTransfers() {
  return [
    const TransferModel(
      transferId: 501,
      reference: 'TX-501-ABC',
      status: TransferStatusType.completed,
      amount: 2500.0,
      currency: 'LKR',
      receiverDisplayName: 'Nimali Silva',
      receiverPhone: '+94771234567',
      receiverId: 2,
      createdAt: '2026-10-12T10:30:00Z',
    ),
    const TransferModel(
      transferId: 502,
      reference: 'TX-502-DEF',
      status: TransferStatusType.pending,
      amount: 1200.0,
      currency: 'LKR',
      receiverDisplayName: 'Saman Fernando',
      receiverPhone: '+94772345678',
      receiverId: 3,
      createdAt: '2026-10-12T09:15:00Z',
    ),
    const TransferModel(
      transferId: 503,
      reference: 'TX-503-GHI',
      status: TransferStatusType.failed,
      amount: 450.0,
      currency: 'LKR',
      receiverDisplayName: 'Priya Rajapaksa',
      receiverPhone: '+94773456789',
      receiverId: 4,
      createdAt: '2026-10-11T14:20:00Z',
    ),
    const TransferModel(
      transferId: 504,
      reference: 'TX-504-JKL',
      status: TransferStatusType.reversed,
      amount: 3000.0,
      currency: 'LKR',
      receiverDisplayName: 'Asanka Bandara',
      receiverPhone: '+94774567890',
      receiverId: 5,
      createdAt: '2026-10-10T18:00:00Z',
    ),
  ];
}

Widget createPayHubTestApp({
  Map<String, bool>? flags,
  TransferService? transferService,
  List<TransferModel>? transfers,
  bool throwTransfersError = false,
  List<String> recordedRoutes = const [],
  void Function(String route, dynamic extra)? onNavigate,
}) {
  final router = GoRouter(
    initialLocation: '/pay',
    routes: [
      GoRoute(
        path: '/pay',
        builder: (context, state) => const PayHubScreen(),
      ),
      GoRoute(
        path: '/send',
        builder: (context, state) {
          onNavigate?.call('/send', state.extra);
          return Scaffold(
            appBar: AppBar(title: const Text('Send Money Screen')),
            body: Center(child: Text('Extra: ${state.extra}')),
          );
        },
      ),
      GoRoute(
        path: '/qr',
        builder: (context, state) {
          onNavigate?.call('/qr', state.extra);
          return Scaffold(
            appBar: AppBar(title: const Text('QR Screen')),
            body: const Center(child: Text('QR Screen Content')),
          );
        },
      ),
      GoRoute(
        path: '/bills',
        builder: (context, state) {
          onNavigate?.call('/bills', state.extra);
          return Scaffold(
            appBar: AppBar(title: const Text('Pay Bills Screen')),
            body: const Center(child: Text('Bills Screen Content')),
          );
        },
      ),
      GoRoute(
        path: '/history',
        builder: (context, state) {
          onNavigate?.call('/history', state.extra);
          return Scaffold(
            appBar: AppBar(title: const Text('History Screen')),
            body: const Center(child: Text('History Screen Content')),
          );
        },
      ),
    ],
  );

  return ProviderScope(
    overrides: [
      if (flags != null)
        featureFlagsProvider.overrideWith(() => FeatureFlagsNotifier(flags)),
      if (transferService != null)
        transferServiceProvider.overrideWithValue(transferService),
      bankAccountServiceProvider.overrideWithValue(FakeBankAccountService()),
      cardServiceProvider.overrideWithValue(FakeCardService()),
      recentTransactionsProvider.overrideWith((ref) async {
        if (throwTransfersError) {
          throw Exception('Failed to load recent transactions');
        }
        return transfers ?? createMockTransfers();
      }),
    ],
    child: MaterialApp.router(
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      routerConfig: router,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
    ),
  );
}

// -----------------------------------------------------------------------------
// Tests
// -----------------------------------------------------------------------------

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('PayHubScreen Layout & Components', () {
    testWidgets('renders Title Pay, Search field, ProductTiles, and Recent Payments', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createPayHubTestApp());
      await tester.pumpAndSettle();

      // Title Pay
      expect(find.byKey(const Key('pay_title')), findsOneWidget);
      expect(find.text('Pay'), findsOneWidget);

      // Search field [pay_search]
      expect(find.byKey(AppTestKeys.paySearch), findsOneWidget);

      // Funding source selector
      expect(find.byKey(AppTestKeys.payFundingSourceSelector), findsOneWidget);

      // 7 ProductTiles
      expect(find.byKey(AppTestKeys.paySend), findsOneWidget);
      expect(find.byKey(AppTestKeys.payQr), findsOneWidget);
      expect(find.byKey(AppTestKeys.payBills), findsOneWidget);
      expect(find.byKey(AppTestKeys.payScheduled), findsOneWidget);
      expect(find.byKey(AppTestKeys.paySplit), findsOneWidget);
      expect(find.byKey(AppTestKeys.payRequest), findsOneWidget);
      expect(find.byKey(AppTestKeys.payBetween), findsOneWidget);

      // Recent Payments header and See all
      expect(find.text('RECENT PAYMENTS'), findsOneWidget);
      expect(find.byKey(AppTestKeys.paySeeAll), findsOneWidget);

      // Recent Payments list container
      expect(find.byKey(AppTestKeys.payRecentPayments), findsOneWidget);
    });

    testWidgets('renders recent recipients with initials avatars and taps navigate to /send', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      String? lastNavigatedRoute;
      dynamic lastNavigatedExtra;

      await tester.pumpWidget(createPayHubTestApp(
        onNavigate: (route, extra) {
          lastNavigatedRoute = route;
          lastNavigatedExtra = extra;
        },
      ));
      await tester.pumpAndSettle();

      // Initials avatars for mock recipients: Nimali Silva (id 2), Saman Fernando (id 3)
      expect(find.byKey(const Key('recipient_2')), findsOneWidget);
      expect(find.text('Nimali'), findsOneWidget);

      // Tap Nimali's avatar
      await tester.tap(find.byKey(const Key('recipient_2')));
      await tester.pumpAndSettle();

      expect(lastNavigatedRoute, '/send');
      expect(lastNavigatedExtra, isA<TransferModel>());
      expect((lastNavigatedExtra as TransferModel).receiverDisplayName, 'Nimali Silva');
    });
  });

  group('PayHubScreen Search & Rate-Limited Lookup', () {
    testWidgets('valid phone lookup navigates to /send with resolved recipient', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      String? lastNavigatedRoute;
      dynamic lastNavigatedExtra;

      final transferService = FakeTransferService(
        onLookup: (phone) async => const LookupResultModel(
          resultType: 'FOUND',
          displayName: 'Kamal Perera',
          verificationStatus: 'VERIFIED',
          accounts: [
            EligibleAccountModel(
              institutionCode: 'BOC',
              bankDisplayName: 'Bank of Ceylon',
              maskedNumber: '****1234',
              isDefault: true,
            ),
          ],
        ),
      );

      await tester.pumpWidget(createPayHubTestApp(
        transferService: transferService,
        onNavigate: (route, extra) {
          lastNavigatedRoute = route;
          lastNavigatedExtra = extra;
        },
      ));
      await tester.pumpAndSettle();

      // Enter valid Sri Lankan mobile number into pay_search
      await tester.enterText(find.byKey(AppTestKeys.paySearch), '0771234567');
      await tester.pumpAndSettle();

      // Tap lookup arrow button
      await tester.tap(find.byKey(const Key('pay_lookup_btn')));
      await tester.pumpAndSettle();

      expect(lastNavigatedRoute, '/send');
      expect(lastNavigatedExtra, isA<LookupResultModel>());
      expect((lastNavigatedExtra as LookupResultModel).displayName, 'Kamal Perera');
    });

    testWidgets('429 rate limit error triggers cooldown warning', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final transferService = FakeTransferService(
        onLookup: (phone) async {
          throw DioException(
            requestOptions: RequestOptions(path: '/lookup'),
            response: Response(
              requestOptions: RequestOptions(path: '/lookup'),
              statusCode: 429,
            ),
          );
        },
      );

      await tester.pumpWidget(createPayHubTestApp(transferService: transferService));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(AppTestKeys.paySearch), '0771234567');
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('pay_lookup_btn')));
      await tester.pumpAndSettle();

      // Cooldown warning is displayed
      expect(find.textContaining('Too many lookup attempts'), findsOneWidget);
    });

    testWidgets('invalid phone shows friendly error message', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createPayHubTestApp());
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(AppTestKeys.paySearch), '123');
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('pay_lookup_btn')));
      await tester.pumpAndSettle();

      expect(find.textContaining('Please enter a valid mobile number'), findsOneWidget);
    });
  });

  group('ProductTiles Actions, Flags, & Coming Soon Sheets', () {
    testWidgets('pay_send navigates to /send', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      String? lastNavigatedRoute;
      await tester.pumpWidget(createPayHubTestApp(
        onNavigate: (route, _) => lastNavigatedRoute = route,
      ));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.paySend));
      await tester.pumpAndSettle();

      expect(lastNavigatedRoute, '/send');
    });

    testWidgets('pay_qr navigates to /qr', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      String? lastNavigatedRoute;
      await tester.pumpWidget(createPayHubTestApp(
        onNavigate: (route, _) => lastNavigatedRoute = route,
      ));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.payQr));
      await tester.pumpAndSettle();

      expect(lastNavigatedRoute, '/qr');
    });

    testWidgets('pay_bills shows coming soon sheet when flagged off', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createPayHubTestApp(
        flags: {FeatureFlagKeys.bills: false},
      ));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.payBills));
      await tester.pumpAndSettle();

      // Coming soon sheet appears
      expect(find.byKey(AppTestKeys.comingSoonSheet), findsOneWidget);
      expect(find.text('Pay Bills - Coming Soon'), findsOneWidget);

      // Dismiss sheet
      await tester.tap(find.byKey(AppTestKeys.comingSoonClose));
      await tester.pumpAndSettle();
      expect(find.byKey(AppTestKeys.comingSoonSheet), findsNothing);
    });

    testWidgets('pay_bills navigates to /bills when flagged on', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      String? lastNavigatedRoute;
      await tester.pumpWidget(createPayHubTestApp(
        flags: {FeatureFlagKeys.bills: true},
        onNavigate: (route, _) => lastNavigatedRoute = route,
      ));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.payBills));
      await tester.pumpAndSettle();

      expect(lastNavigatedRoute, '/bills');
    });

    testWidgets('pay_scheduled shows coming soon sheet when flagged off', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createPayHubTestApp(
        flags: {FeatureFlagKeys.scheduled: false},
      ));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.payScheduled));
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.comingSoonSheet), findsOneWidget);
      expect(find.text('Scheduled Payments - Coming Soon'), findsOneWidget);

      await tester.tap(find.byKey(AppTestKeys.comingSoonClose));
      await tester.pumpAndSettle();
      expect(find.byKey(AppTestKeys.comingSoonSheet), findsNothing);
    });

    testWidgets('pay_split shows coming soon sheet when flagged off', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createPayHubTestApp(
        flags: {FeatureFlagKeys.split: false},
      ));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.paySplit));
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.comingSoonSheet), findsOneWidget);
      expect(find.text('Split a Bill - Coming Soon'), findsOneWidget);

      await tester.tap(find.byKey(AppTestKeys.comingSoonClose));
      await tester.pumpAndSettle();
      expect(find.byKey(AppTestKeys.comingSoonSheet), findsNothing);
    });

    testWidgets('pay_request navigates to /qr payment requests', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      String? lastNavigatedRoute;
      await tester.pumpWidget(createPayHubTestApp(
        onNavigate: (route, _) => lastNavigatedRoute = route,
      ));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.payRequest));
      await tester.pumpAndSettle();

      expect(lastNavigatedRoute, '/qr');
    });

    testWidgets('pay_between shows clear not available dialog', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createPayHubTestApp());
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.payBetween));
      await tester.pumpAndSettle();

      expect(find.text('Between Accounts'), findsOneWidget);
      expect(find.text('Transfers between your own accounts are not available in this version.'), findsOneWidget);

      // Dismiss dialog
      await tester.tap(find.text('OK'));
      await tester.pumpAndSettle();
      expect(find.text('Between Accounts'), findsNothing);
    });
  });

  group('Funding Source Choice & Recent Payments States', () {
    testWidgets('payFundingSourceSelector displays source and allows switching', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createPayHubTestApp());
      await tester.pumpAndSettle();

      // Active selector pill
      expect(find.byKey(AppTestKeys.payFundingSourceSelector), findsOneWidget);
      expect(find.text('Paying from'), findsOneWidget);

      // Tap selector
      await tester.tap(find.byKey(AppTestKeys.payFundingSourceSelector));
      await tester.pumpAndSettle();

      // Modal bottom sheet opens
      expect(find.text('Select Payment Source'), findsOneWidget);
      expect(find.byKey(const Key('pay_from_account_1')), findsOneWidget);
      expect(find.byKey(const Key('pay_from_account_2')), findsOneWidget);
      expect(find.byKey(const Key('pay_from_card_101')), findsOneWidget);

      // Select Card 101
      await tester.tap(find.byKey(const Key('pay_from_card_101')));
      await tester.pumpAndSettle();

      // Sheet closes and pill updates to card
      expect(find.text('Select Payment Source'), findsNothing);
      expect(find.text('Test Card •••• 0010'), findsOneWidget);
    });

    testWidgets('renders recent payments with status labels Completed, Pending, Failed, Reversed', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createPayHubTestApp());
      await tester.pumpAndSettle();

      // Check all 4 statuses
      expect(find.text('Completed'), findsOneWidget);
      expect(find.text('Pending'), findsOneWidget);
      expect(find.text('Failed'), findsOneWidget);
      expect(find.text('Reversed'), findsOneWidget);

      // Check payment amounts
      expect(find.text('LKR 2500.00'), findsOneWidget);
      expect(find.text('LKR 1200.00'), findsOneWidget);
      expect(find.text('LKR 450.00'), findsOneWidget);
      expect(find.text('LKR 3000.00'), findsOneWidget);

      // Tap See all navigates to /history
      String? lastNavigatedRoute;
      await tester.pumpWidget(createPayHubTestApp(
        onNavigate: (route, _) => lastNavigatedRoute = route,
      ));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.paySeeAll));
      await tester.pumpAndSettle();
      expect(lastNavigatedRoute, '/history');
    });

    testWidgets('renders empty state when there are no recent payments', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createPayHubTestApp(transfers: []));
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.payRecentEmpty), findsOneWidget);
      expect(find.text('No recent payments'), findsOneWidget);
    });

    testWidgets('renders error state and retry button when loading fails', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createPayHubTestApp(throwTransfersError: true));
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.payRecentError), findsOneWidget);
      expect(find.text('Could not load recent payments.'), findsOneWidget);
      expect(find.text('Retry'), findsOneWidget);
    });
  });
}
