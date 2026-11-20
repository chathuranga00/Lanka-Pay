import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/features/accounts/data/bank_account_service.dart';
import 'package:lankapay_id/features/accounts/domain/bank_account_model.dart';
import 'package:lankapay_id/features/home/data/home_providers.dart';
import 'package:lankapay_id/features/qr/presentation/qr_scanner_screen.dart';
import 'package:lankapay_id/features/send/data/transfer_service.dart';
import 'package:lankapay_id/features/send/domain/transfer_models.dart';
import 'package:lankapay_id/features/send/presentation/send_screen.dart';
import 'package:lankapay_id/features/send/presentation/transfer_result_screen.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';

class MockTransferService implements TransferService {
  LookupResultModel? lookupResult;
  Exception? lookupException;
  QuoteModel? quoteResult;
  TransferModel? initiateResult;
  String? lastIdempotencyKey;
  String? lastPin;

  @override
  Future<LookupResultModel> lookupRecipient(String phone) async {
    if (lookupException != null) throw lookupException!;
    if (lookupResult != null) return lookupResult!;
    return const LookupResultModel(
      resultType: 'FOUND',
      displayName: 'Kamal Perera',
      verificationStatus: 'VERIFIED',
      accounts: [
        EligibleAccountModel(
          institutionCode: 'SIMULATED_BANK_A',
          bankDisplayName: 'Simulated Bank A',
          maskedNumber: '****1001',
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
    if (quoteResult != null) return quoteResult!;
    return QuoteModel(
      quoteReference: 'QUO-REDESIGN-123',
      payeeId: 42,
      payeeDisplayName: 'Kamal Perera',
      amount: amount,
      fee: 0.0,
      totalAmount: amount,
      currency: currency,
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
    lastIdempotencyKey = idempotencyKey;
    lastPin = pin;
    if (initiateResult != null) return initiateResult!;
    return TransferModel(
      transferId: 200,
      reference: 'TX-REF-REDESIGN-200',
      status: TransferStatusType.completed,
      amount: amount,
      currency: currency,
      receiverDisplayName: 'Kamal Perera',
      bankTxRef: 'BANK-CONFIRMED-200',
      note: note,
    );
  }

  @override
  Future<TransferModel> challengeTransfer({
    required int transferId,
    required String otp,
  }) async {
    if (initiateResult != null) return initiateResult!;
    return TransferModel(
      transferId: transferId,
      reference: 'TX-REF-CHALLENGED-$transferId',
      status: TransferStatusType.completed,
      amount: 100.0,
      currency: 'LKR',
      receiverDisplayName: 'Kamal Perera',
    );
  }

  @override
  Future<TransferModel> getTransferById(int transferId) async {
    return TransferModel(
      transferId: transferId,
      reference: 'TX-REF-REDESIGN-$transferId',
      status: TransferStatusType.completed,
      amount: 1500.0,
      currency: 'LKR',
    );
  }
}

class MockBankAccountService implements BankAccountService {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);

  @override
  Future<List<BankAccountModel>> listAccounts() async {
    return const [
      BankAccountModel(
        id: 1,
        institutionCode: 'SIMULATED_BOC',
        accountName: 'Personal Checking',
        maskedNumber: '****5678',
        status: 'ACTIVE',
        isDefault: true,
        discoverable: true,
        receivingEnabled: true,
      ),
    ];
  }
}

Widget createTestSendScreen({
  required MockTransferService transferService,
  List<TransferModel>? recentTransfers,
}) {
  final transfers = recentTransfers ??
      const [
        TransferModel(
          transferId: 101,
          reference: 'TX-101',
          status: TransferStatusType.completed,
          amount: 2500.0,
          receiverId: 42,
          receiverDisplayName: 'Nimal Silva',
          receiverPhone: '+94771234567',
        ),
        TransferModel(
          transferId: 102,
          reference: 'TX-102',
          status: TransferStatusType.completed,
          amount: 1500.0,
          receiverId: 99,
          receiverDisplayName: 'Kamal Perera',
          receiverPhone: '+94779876543',
        ),
      ];

  return ProviderScope(
    overrides: [
      transferServiceProvider.overrideWithValue(transferService),
      bankAccountServiceProvider.overrideWithValue(MockBankAccountService()),
      recentTransactionsProvider.overrideWith((ref) => Future.value(transfers)),
    ],
    child: MaterialApp(
      theme: AppTheme.lightTheme,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      home: const SendScreen(),
    ),
  );
}

void main() {
  setUp(() {
    TestWidgetsFlutterBinding.ensureInitialized();
  });

  group('SendScreen Redesign Tests', () {
    testWidgets('Header renders Send title and QR icon opens scanner', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockService = MockTransferService();
      await tester.pumpWidget(createTestSendScreen(transferService: mockService));
      await tester.pumpAndSettle();

      expect(find.text('Send'), findsWidgets);
      expect(find.byKey(const Key('send_scan_qr')), findsOneWidget);

      // Tap QR icon in header
      await tester.tap(find.byKey(const Key('send_scan_qr')));
      await tester.pumpAndSettle();

      // Verify QR scanner is opened
      expect(find.byType(QrScannerScreen), findsOneWidget);
    });

    testWidgets('Search field handles invalid number state', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockService = MockTransferService();
      await tester.pumpWidget(createTestSendScreen(transferService: mockService));
      await tester.pumpAndSettle();

      // Enter invalid number
      await tester.enterText(find.byKey(const Key('send_search')), '12345');
      await tester.tap(find.byKey(const Key('send_lookup_btn')));
      await tester.pumpAndSettle();

      expect(
        find.text('Please enter a valid Sri Lankan mobile number (e.g. 0771234567 or +94771234567).'),
        findsOneWidget,
      );
    });

    testWidgets('Search handles NOT_FOUND (not registered) state', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockService = MockTransferService();
      mockService.lookupResult = const LookupResultModel(resultType: 'NOT_FOUND');

      await tester.pumpWidget(createTestSendScreen(transferService: mockService));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('send_search')), '0771234567');
      await tester.tap(find.byKey(const Key('send_lookup_btn')));
      await tester.pumpAndSettle();

      expect(
        find.text('No LankaPay-ID account found for this mobile number. Make sure the number is registered.'),
        findsOneWidget,
      );
    });

    testWidgets('Search handles DISCOVERY_DISABLED state', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockService = MockTransferService();
      mockService.lookupResult = const LookupResultModel(resultType: 'DISCOVERY_DISABLED');

      await tester.pumpWidget(createTestSendScreen(transferService: mockService));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('send_search')), '0771234567');
      await tester.tap(find.byKey(const Key('send_lookup_btn')));
      await tester.pumpAndSettle();

      expect(
        find.text('The recipient has disabled phone-number discovery for privacy under PDPA.'),
        findsOneWidget,
      );
    });

    testWidgets('Search handles NO_ELIGIBLE_ACCOUNT state', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockService = MockTransferService();
      mockService.lookupResult = const LookupResultModel(resultType: 'NO_ELIGIBLE_ACCOUNT');

      await tester.pumpWidget(createTestSendScreen(transferService: mockService));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('send_search')), '0771234567');
      await tester.tap(find.byKey(const Key('send_lookup_btn')));
      await tester.pumpAndSettle();

      expect(
        find.text('The recipient is registered but has no linked bank accounts configured to receive payments.'),
        findsOneWidget,
      );
    });

    testWidgets('Search handles PRIVACY_RESTRICTED (unavailable) state', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockService = MockTransferService();
      mockService.lookupResult = const LookupResultModel(resultType: 'PRIVACY_RESTRICTED');

      await tester.pumpWidget(createTestSendScreen(transferService: mockService));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('send_search')), '0771234567');
      await tester.tap(find.byKey(const Key('send_lookup_btn')));
      await tester.pumpAndSettle();

      expect(
        find.text('Unable to route transfer to this user due to privacy preferences.'),
        findsOneWidget,
      );
    });

    testWidgets('Search handles verification incomplete state', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockService = MockTransferService();
      mockService.lookupResult = const LookupResultModel(
        resultType: 'FOUND',
        displayName: 'Kamal Perera',
        verificationStatus: 'PENDING_DOCUMENT_VERIFICATION',
        accounts: [
          EligibleAccountModel(
            institutionCode: 'SIMULATED_BANK_A',
            bankDisplayName: 'Simulated Bank A',
            maskedNumber: '****1001',
            isDefault: true,
          ),
        ],
      );

      await tester.pumpWidget(createTestSendScreen(transferService: mockService));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('send_search')), '0771234567');
      await tester.tap(find.byKey(const Key('send_lookup_btn')));
      await tester.pumpAndSettle();

      expect(
        find.text('Recipient verification is incomplete or pending. They cannot receive payments yet.'),
        findsOneWidget,
      );
    });

    testWidgets('Search handles service down state', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockService = MockTransferService();
      mockService.lookupException = DioException(
        requestOptions: RequestOptions(path: '/payment-identities/lookup'),
        response: Response(
          requestOptions: RequestOptions(path: '/payment-identities/lookup'),
          statusCode: 503,
        ),
      );

      await tester.pumpWidget(createTestSendScreen(transferService: mockService));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('send_search')), '0771234567');
      await tester.tap(find.byKey(const Key('send_lookup_btn')));
      await tester.pumpAndSettle();

      expect(
        find.text('Payment registry service is temporarily unavailable. Please try again later.'),
        findsOneWidget,
      );
    });

    testWidgets('Search handles rate limit (too many attempts) and cooldown countdown', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockService = MockTransferService();
      mockService.lookupException = DioException(
        requestOptions: RequestOptions(path: '/payment-identities/lookup'),
        response: Response(
          requestOptions: RequestOptions(path: '/payment-identities/lookup'),
          statusCode: 429,
        ),
      );

      await tester.pumpWidget(createTestSendScreen(transferService: mockService));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('send_search')), '0771234567');
      await tester.tap(find.byKey(const Key('send_lookup_btn')));
      await tester.pumpAndSettle();

      expect(
        find.text('Too many lookup attempts. Please wait a moment before trying again.'),
        findsOneWidget,
      );
      // Cooldown badge visible
      expect(find.text('30s'), findsOneWidget);

      // Attempting again triggers cooldown message
      await tester.tap(find.byKey(const Key('send_lookup_btn')));
      await tester.pump();
      expect(find.textContaining('Too many lookup attempts'), findsOneWidget);
    });

    testWidgets('Recent recipients row renders "+ Send" and distinct recipients', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockService = MockTransferService();
      await tester.pumpWidget(createTestSendScreen(transferService: mockService));
      await tester.pumpAndSettle();

      // "+ Send" circle button [send_new]
      expect(find.byKey(const Key('send_new')), findsOneWidget);

      // Distinct recipients: Nimal (id: 42), Kamal (id: 99)
      expect(find.byKey(const Key('recipient_42')), findsOneWidget);
      expect(find.byKey(const Key('recipient_99')), findsOneWidget);
      expect(find.text('Nimal'), findsOneWidget);
      expect(find.text('Kamal'), findsOneWidget);

      // Tapping "+ Send" focuses search field
      await tester.tap(find.byKey(const Key('send_new')));
      await tester.pump();

      // Tapping recipient_42 starts normal flow at recipient verification
      await tester.tap(find.byKey(const Key('recipient_42')));
      await tester.pumpAndSettle();

      expect(find.text('Recipient Verification'), findsOneWidget);
      expect(find.text('Kamal Perera'), findsOneWidget);
      expect(find.text('Receiving: Simulated Bank A (****1001)'), findsOneWidget);
    });

    testWidgets('MenuTiles 2x2 grid renders and each tile responds appropriately', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockService = MockTransferService();
      await tester.pumpWidget(createTestSendScreen(transferService: mockService));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('tile_pay_by_phone')), findsOneWidget);
      expect(find.byKey(const Key('tile_scan_qr')), findsOneWidget);
      expect(find.byKey(const Key('tile_between_accounts')), findsOneWidget);
      expect(find.byKey(const Key('tile_requests')), findsOneWidget);

      // Tap tile_pay_by_phone (focuses search field)
      await tester.tap(find.byKey(const Key('tile_pay_by_phone')));
      await tester.pump();

      // Tap tile_between_accounts shows "Not available in this version"
      await tester.tap(find.byKey(const Key('tile_between_accounts')));
      await tester.pumpAndSettle();
      expect(
        find.text('Transfers between your own accounts are not available in this version.'),
        findsOneWidget,
      );
      // Dismiss dialog
      await tester.tap(find.text('OK'));
      await tester.pumpAndSettle();

      // Tap tile_scan_qr opens QR scanner screen
      await tester.tap(find.byKey(const Key('tile_scan_qr')));
      await tester.pumpAndSettle();
      expect(find.byType(QrScannerScreen), findsOneWidget);
    });

    testWidgets('Full happy path from search to SUCCESS result screen', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final mockService = MockTransferService();
      mockService.lookupResult = const LookupResultModel(
        resultType: 'FOUND',
        displayName: 'Kamal Perera',
        verificationStatus: 'VERIFIED',
        accounts: [
          EligibleAccountModel(
            institutionCode: 'SIMULATED_BANK_A',
            bankDisplayName: 'Simulated Bank A',
            maskedNumber: '****1001',
            isDefault: true,
          ),
        ],
      );

      final router = GoRouter(
        initialLocation: '/send',
        routes: [
          GoRoute(
            path: '/send',
            builder: (context, state) => const SendScreen(),
          ),
          GoRoute(
            path: '/send/result',
            builder: (context, state) {
              final transfer = state.extra as TransferModel;
              return TransferResultScreen(initialTransfer: transfer);
            },
          ),
        ],
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            transferServiceProvider.overrideWithValue(mockService),
            bankAccountServiceProvider.overrideWithValue(MockBankAccountService()),
            recentTransactionsProvider.overrideWith((ref) => Future.value(const [])),
          ],
          child: MaterialApp.router(
            theme: AppTheme.lightTheme,
            localizationsDelegates: const [
              AppLocalizations.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: AppLocalizations.supportedLocales,
            routerConfig: router,
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Step 0: Search with valid phone
      await tester.enterText(find.byKey(const Key('send_search')), '0771234567');
      await tester.tap(find.byKey(const Key('send_lookup_btn')));
      await tester.pumpAndSettle();

      // Step 1: Verified payee card & account selection
      expect(find.text('Kamal Perera'), findsOneWidget);
      expect(find.text('Receiving: Simulated Bank A (****1001)'), findsOneWidget);

      await tester.enterText(find.byKey(const Key('send_amount_field')), '1500.00');
      await tester.enterText(find.byKey(const Key('send_note_field')), 'Dinner split');

      await tester.tap(find.byKey(const Key('send_review_btn')));
      await tester.pumpAndSettle();

      // Step 2: Confirmation bottom sheet
      expect(find.text('Confirm Payment Details'), findsOneWidget);
      expect(find.text('Recipient Name'), findsOneWidget);
      expect(find.text('Recipient Bank'), findsOneWidget);
      expect(find.text('Receiving Account'), findsOneWidget);
      expect(find.text('LKR 1500.00'), findsNWidgets(2));
      expect(find.text('Dinner split'), findsNWidgets(2)); // in TextField and confirmation sheet

      await tester.tap(find.byKey(const Key('confirmation_continue_btn')));
      await tester.pumpAndSettle();

      // Step 3: PIN authorization
      expect(find.text('Step-Up Authentication'), findsOneWidget);
      await tester.enterText(find.byKey(const Key('transfer_pin_input')), '123456');
      await tester.tap(find.byKey(const Key('transfer_confirm_pin_btn')));
      await tester.pumpAndSettle();

      // Step 4: SUCCESS result screen confirmed
      expect(find.text('Payment Confirmed'), findsOneWidget);
      expect(find.text('Bank adapter confirmed debit and credit transfer.'), findsOneWidget);
      expect(find.text('LKR 1500.00'), findsOneWidget);
      expect(find.text('TX-REF-REDESIGN-200'), findsOneWidget);
      expect(find.text('BANK-CONFIRMED-200'), findsOneWidget);
      expect(find.text('COMPLETED'), findsOneWidget);

      // Verify Idempotency key per attempt
      expect(mockService.lastIdempotencyKey, isNotNull);
      expect(mockService.lastIdempotencyKey!.startsWith('IDEM-'), isTrue);
      expect(mockService.lastPin, '123456');
    });
  });
}
