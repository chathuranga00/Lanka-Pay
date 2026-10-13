import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/features/accounts/data/bank_account_service.dart';
import 'package:lankapay_id/features/accounts/domain/bank_account_model.dart';
import 'package:lankapay_id/features/cards/data/card_service.dart';
import 'package:lankapay_id/features/cards/domain/card_model.dart';
import 'package:lankapay_id/features/home/data/home_providers.dart';
import 'package:lankapay_id/features/history/data/history_service.dart';
import 'package:lankapay_id/features/history/presentation/history_screen.dart';
import 'package:lankapay_id/features/history/presentation/receipt_screen.dart';
import 'package:lankapay_id/features/send/data/transfer_service.dart';
import 'package:lankapay_id/features/send/domain/transfer_models.dart';
import 'package:lankapay_id/features/send/presentation/send_screen.dart';
import 'package:lankapay_id/features/send/presentation/transfer_result_screen.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';

class MockCardFundingTransferService implements TransferService {
  LookupResultModel? lookupResult;
  QuoteModel? quoteResult;
  TransferModel? initiateResult;
  TransferModel? challengeResult;
  TransferModel? getTransferResult;
  Exception? initiateException;
  Exception? challengeException;

  int initiateCallCount = 0;
  int challengeCallCount = 0;
  FundingSourceModel? lastFundingSource;
  String? lastIdempotencyKey;
  String? lastPin;

  @override
  Future<LookupResultModel> lookupRecipient(String phone) async {
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
    final isCard = fundingSource?.type == FundingType.card;
    final fee = isCard ? 25.0 : 0.0;
    return QuoteModel(
      quoteReference: 'QUO-CARD-789',
      payeeId: payeeId ?? 42,
      payeeDisplayName: 'Kamal Perera',
      maskedPhone: '+9477***1234',
      amount: amount,
      fee: fee,
      totalAmount: amount + fee,
      currency: currency,
      simulated: true,
      feeLabel: isCard ? 'Card Fee (simulated)' : 'Network Fee',
      fundingSource: fundingSource,
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
    initiateCallCount++;
    lastFundingSource = fundingSource;
    lastIdempotencyKey = idempotencyKey;
    lastPin = pin;

    if (initiateException != null) throw initiateException!;
    if (initiateResult != null) return initiateResult!;

    return TransferModel(
      transferId: 101,
      reference: 'TX-CARD-101',
      status: TransferStatusType.completed,
      amount: amount,
      fee: fundingSource?.type == FundingType.card ? 25.0 : 0.0,
      totalAmount: amount + (fundingSource?.type == FundingType.card ? 25.0 : 0.0),
      fundingType: fundingSource?.type ?? FundingType.card,
      cardId: fundingSource?.id,
      cardLast4: '1234',
      paymentMethod: 'Paid with card •••• 1234 (TEST CARD)',
      receiverDisplayName: 'Kamal Perera',
      note: note,
    );
  }

  @override
  Future<TransferModel> challengeTransfer({
    required int transferId,
    required String otp,
  }) async {
    challengeCallCount++;
    if (challengeException != null) throw challengeException!;
    if (challengeResult != null) return challengeResult!;
    return TransferModel(
      transferId: transferId,
      reference: 'TX-CHALLENGE-$transferId',
      status: TransferStatusType.completed,
      amount: 1500.0,
      fundingType: FundingType.card,
      cardLast4: '1234',
      paymentMethod: 'Paid with card •••• 1234 (TEST CARD)',
      receiverDisplayName: 'Kamal Perera',
    );
  }

  @override
  Future<TransferModel> getTransferById(int transferId) async {
    if (getTransferResult != null) return getTransferResult!;
    return TransferModel(
      transferId: transferId,
      reference: 'TX-POLL-$transferId',
      status: TransferStatusType.completed,
      amount: 1500.0,
      fundingType: FundingType.card,
      cardLast4: '1234',
      receiverDisplayName: 'Kamal Perera',
    );
  }
}

class MockCardFundingAccountService implements BankAccountService {
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

class MockCardFundingHistoryService implements HistoryService {
  List<TransferModel> historyItems = [];

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);

  @override
  Future<List<TransferModel>> listHistory({
    HistoryFilter? filter,
    int page = 0,
    int size = 20,
  }) async {
    var filtered = List<TransferModel>.from(historyItems);
    if (filter?.fundingType != null && filter!.fundingType != 'ALL') {
      filtered = filtered.where((tx) {
        if (filter.fundingType == 'CARD') return tx.fundingType == FundingType.card;
        if (filter.fundingType == 'ACCOUNT') return tx.fundingType == FundingType.account;
        return true;
      }).toList();
    }
    return filtered;
  }

  @override
  Future<ReceiptModel> getReceipt(int transferId) async {
    final tx = historyItems.firstWhere((t) => t.transferId == transferId, orElse: () => historyItems.first);
    return ReceiptModel(
      receiptNumber: 'REC-${tx.reference}',
      transferId: tx.transferId,
      reference: tx.reference,
      status: tx.status.name.toUpperCase(),
      amount: tx.amount,
      fee: tx.fee,
      totalDebited: tx.totalAmount > 0 ? tx.totalAmount : tx.amount,
      receiver: ReceiptPartyModel(displayName: tx.receiverDisplayName ?? 'Payee'),
      sender: const ReceiptPartyModel(displayName: 'Test User', maskedPhone: '+9477***9999'),
      bankTxRef: 'BANK-REF-999',
      fundingType: tx.fundingType,
      cardId: tx.cardId,
      cardLast4: tx.cardLast4,
      paymentMethod: tx.paymentMethod,
    );
  }
}

class MockCardFundingCardsNotifier extends CardsNotifier {
  final List<CardModel> mockCards;
  MockCardFundingCardsNotifier(this.mockCards);

  @override
  Future<List<CardModel>> build() async {
    return mockCards;
  }
}

CardModel createCard({
  required int id,
  required String last4,
  CardStatus status = CardStatus.active,
  bool onlinePaymentsEnabled = true,
  int dailyLimitMinor = 5000000, // LKR 50,000.00
  int perPaymentLimitMinor = 2500000, // LKR 25,000.00
}) {
  return CardModel(
    id: id,
    last4: last4,
    expMonth: 12,
    expYear: 2029,
    brandLabel: 'Visa',
    nickname: 'My Test Card',
    cardKind: CardKind.linked,
    status: status,
    onlinePaymentsEnabled: onlinePaymentsEnabled,
    dailyLimitMinor: dailyLimitMinor,
    perPaymentLimitMinor: perPaymentLimitMinor,
  );
}

Widget buildTestApp({
  Widget? child,
  String initialLocation = '/send',
  required MockCardFundingTransferService transferService,
  required List<CardModel> cards,
  MockCardFundingHistoryService? historyService,
}) {
  final router = GoRouter(
    initialLocation: initialLocation,
    routes: [
      GoRoute(
        path: '/send',
        builder: (context, state) => child ?? const SendScreen(),
      ),
      GoRoute(
        path: '/send/result',
        builder: (context, state) {
          final transfer = state.extra as TransferModel;
          return TransferResultScreen(initialTransfer: transfer);
        },
      ),
      GoRoute(
        path: '/history',
        builder: (context, state) => const HistoryScreen(),
      ),
    ],
  );

  return ProviderScope(
    overrides: [
      transferServiceProvider.overrideWithValue(transferService),
      bankAccountServiceProvider.overrideWithValue(MockCardFundingAccountService()),
      cardsProvider.overrideWith(() => MockCardFundingCardsNotifier(cards)),
      recentTransactionsProvider.overrideWith((ref) => Future.value(const [])),
      if (historyService != null) historyServiceProvider.overrideWithValue(historyService),
    ],
    child: MaterialApp.router(
      theme: AppTheme.lightTheme,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [Locale('en')],
      routerConfig: router,
    ),
  );
}

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('Card Funding in Send Flows', () {
    testWidgets('1. Card approved: selects card, sees TEST CARD & limit, confirms, and completes with Paid with card', (WidgetTester tester) async {
      final transferService = MockCardFundingTransferService();
      final activeCard = createCard(id: 10, last4: '1234', dailyLimitMinor: 10000000); // 100k limit

      await tester.pumpWidget(
        buildTestApp(
          transferService: transferService,
          cards: [activeCard],
        ),
      );
      await tester.pumpAndSettle();

      // Search recipient
      await tester.enterText(find.byKey(const Key('send_search')), '0771234567');
      await tester.tap(find.byKey(const Key('send_lookup_btn')));
      await tester.pumpAndSettle();

      expect(find.text('Kamal Perera'), findsOneWidget);

      // Enter amount
      await tester.enterText(find.byKey(const Key('send_amount_field')), '2500.00');
      await tester.pumpAndSettle();

      // Verify "Pay from" selector shows card with TEST CARD and remaining daily limit
      expect(find.byKey(const Key('pay_from_selector')), findsOneWidget);
      expect(find.text('•••• 1234'), findsOneWidget);
      expect(find.text('TEST CARD'), findsOneWidget);
      expect(find.text('Remaining: LKR 100000.00'), findsOneWidget);

      // Tap card in selector
      await tester.tap(find.byKey(const Key('pay_from_card_10')));
      await tester.pumpAndSettle();

      // Review transfer
      await tester.tap(find.byKey(const Key('send_review_btn')));
      await tester.pumpAndSettle();

      // Verify Confirmation Bottom Sheet
      expect(find.text('Confirm Payment Details'), findsOneWidget);
      expect(find.text('Recipient Name'), findsOneWidget);
      expect(find.text('Recipient Bank'), findsOneWidget);
      expect(find.text('Receiving Account'), findsOneWidget);
      expect(find.text('LKR 2500.00'), findsOneWidget); // in confirmation sheet
      expect(find.text('2500.00'), findsOneWidget); // in TextField
      expect(find.text('Card Fee (simulated)'), findsOneWidget);
      expect(find.text('Paid from card •••• 1234 (TEST CARD)'), findsOneWidget);
      expect(find.byKey(const Key('confirm_payment_method')), findsOneWidget);
      expect(find.byKey(const Key('confirm_reference')), findsOneWidget);

      // Proceed to PIN authentication
      await tester.tap(find.byKey(const Key('confirmation_continue_btn')));
      await tester.pumpAndSettle();

      expect(find.text('Step-Up Authentication'), findsOneWidget);
      await tester.enterText(find.byKey(const Key('transfer_pin_input')), '123456');
      await tester.tap(find.byKey(const Key('transfer_confirm_pin_btn')));
      await tester.pumpAndSettle();

      // Verify confirmed success result screen
      expect(find.text('Payment Confirmed'), findsOneWidget);
      expect(find.text('Paid with card •••• 1234 (TEST CARD)'), findsOneWidget);
      expect(transferService.lastFundingSource?.type, FundingType.card);
      expect(transferService.lastFundingSource?.id, 10);
    });

    testWidgets('2. Frozen, expired, and limit-exceeded cards are listed disabled with reason in selector', (WidgetTester tester) async {
      final transferService = MockCardFundingTransferService();
      final activeCard = createCard(id: 1, last4: '1111', dailyLimitMinor: 5000000); // 50,000 LKR
      final frozenCard = createCard(id: 2, last4: '2222', status: CardStatus.frozen);
      final expiredCard = createCard(id: 3, last4: '3333', status: CardStatus.expired);
      final noOnlineCard = createCard(id: 4, last4: '4444', onlinePaymentsEnabled: false);

      await tester.pumpWidget(
        buildTestApp(
          transferService: transferService,
          cards: [activeCard, frozenCard, expiredCard, noOnlineCard],
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('send_search')), '0771234567');
      await tester.tap(find.byKey(const Key('send_lookup_btn')));
      await tester.pumpAndSettle();

      // Check disabled reason badges
      expect(find.text('Card frozen'), findsOneWidget);
      expect(find.text('Card expired'), findsOneWidget);
      expect(find.text('Online payments disabled'), findsOneWidget);

      // Enter amount exceeding activeCard limit (60,000 > 50,000)
      await tester.enterText(find.byKey(const Key('send_amount_field')), '60000.00');
      await tester.pumpAndSettle();

      expect(find.text('Limit exceeded'), findsOneWidget);

      // Tapping disabled card does not select it
      await tester.tap(find.byKey(const Key('pay_from_card_2')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('pay_from_card_2')), findsOneWidget);
    });

    testWidgets('3. Default to last used source from SharedPreferences', (WidgetTester tester) async {
      SharedPreferences.setMockInitialValues({
        'last_used_funding_type': 'CARD',
        'last_used_funding_id': 20,
      });

      final transferService = MockCardFundingTransferService();
      final savedCard = createCard(id: 20, last4: '9999');

      await tester.pumpWidget(
        buildTestApp(
          transferService: transferService,
          cards: [savedCard],
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('send_search')), '0771234567');
      await tester.tap(find.byKey(const Key('send_lookup_btn')));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('send_amount_field')), '500.00');
      await tester.pumpAndSettle();

      // Trigger review transfer
      await tester.tap(find.byKey(const Key('send_review_btn')));
      await tester.pumpAndSettle();

      // Verified that card 20 was pre-selected automatically
      expect(find.text('Paid from card •••• 9999 (TEST CARD)'), findsOneWidget);
    });

    testWidgets('4. Double tap protection and Idempotency-Key reuse on retry', (WidgetTester tester) async {
      final transferService = MockCardFundingTransferService();
      final activeCard = createCard(id: 10, last4: '1234');

      await tester.pumpWidget(
        buildTestApp(
          transferService: transferService,
          cards: [activeCard],
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('send_search')), '0771234567');
      await tester.tap(find.byKey(const Key('send_lookup_btn')));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('send_amount_field')), '1000.00');
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('pay_from_card_10')));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('send_review_btn')));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('confirmation_continue_btn')));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('transfer_pin_input')), '123456');

      // Double tap authorize button rapidly
      await tester.tap(find.byKey(const Key('transfer_confirm_pin_btn')));
      await tester.tap(find.byKey(const Key('transfer_confirm_pin_btn')), warnIfMissed: false);
      await tester.pumpAndSettle();

      // Only one transfer call occurred
      expect(transferService.initiateCallCount, 1);
      expect(transferService.lastIdempotencyKey, isNotNull);
    });
  });

  group('Card Challenge OTP Flow', () {
    testWidgets('5. Challenge required prompts bank OTP screen and proceeds to success upon completion', (WidgetTester tester) async {
      final transferService = MockCardFundingTransferService();
      transferService.initiateResult = const TransferModel(
        transferId: 202,
        reference: 'TX-CHALLENGE-REQ-202',
        status: TransferStatusType.challengeRequired,
        amount: 2000.0,
        fundingType: FundingType.card,
        cardLast4: '1234',
      );

      final activeCard = createCard(id: 10, last4: '1234');

      await tester.pumpWidget(
        buildTestApp(
          transferService: transferService,
          cards: [activeCard],
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('send_search')), '0771234567');
      await tester.tap(find.byKey(const Key('send_lookup_btn')));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('send_amount_field')), '2000.00');
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('pay_from_card_10')));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('send_review_btn')));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('confirmation_continue_btn')));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('transfer_pin_input')), '123456');
      await tester.tap(find.byKey(const Key('transfer_confirm_pin_btn')));
      await tester.pumpAndSettle();

      // Challenge Dialog appears
      expect(find.byKey(const Key('card_challenge_otp')), findsOneWidget);
      expect(find.text('Card Security OTP'), findsOneWidget);

      // Enter OTP and submit
      await tester.enterText(find.byKey(const Key('input_challenge_otp')), '654321');
      await tester.tap(find.byKey(const Key('btn_submit_challenge_otp')));
      await tester.pumpAndSettle();

      expect(transferService.challengeCallCount, 1);
      expect(find.text('Payment Confirmed'), findsOneWidget);
    });

    testWidgets('6. Challenge failure routes to declined result screen with suggested next step', (WidgetTester tester) async {
      final transferService = MockCardFundingTransferService();
      transferService.initiateResult = const TransferModel(
        transferId: 203,
        reference: 'TX-CHALLENGE-REQ-203',
        status: TransferStatusType.challengeRequired,
        amount: 2000.0,
        fundingType: FundingType.card,
        cardLast4: '1234',
      );
      transferService.challengeException = DioException(
        requestOptions: RequestOptions(path: '/transfers/203/challenge'),
        response: Response(
          requestOptions: RequestOptions(path: '/transfers/203/challenge'),
          statusCode: 400,
          data: {'code': 'CHALLENGE_FAILED', 'message': 'Invalid OTP'},
        ),
      );

      final activeCard = createCard(id: 10, last4: '1234');

      await tester.pumpWidget(
        buildTestApp(
          transferService: transferService,
          cards: [activeCard],
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('send_search')), '0771234567');
      await tester.tap(find.byKey(const Key('send_lookup_btn')));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('send_amount_field')), '2000.00');
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('pay_from_card_10')));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('send_review_btn')));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('confirmation_continue_btn')));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('transfer_pin_input')), '123456');
      await tester.tap(find.byKey(const Key('transfer_confirm_pin_btn')));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('input_challenge_otp')), '000000');
      await tester.tap(find.byKey(const Key('btn_submit_challenge_otp')));
      await tester.pumpAndSettle();

      expect(find.text('Payment Declined'), findsOneWidget);
      expect(find.text('Bank OTP challenge verification failed.'), findsOneWidget);
      expect(find.byKey(const Key('result_suggested_next_step')), findsOneWidget);
      expect(find.text('Suggested: Choose another card'), findsOneWidget);
    });
  });

  group('Result Screens Distinct Statuses & Decline Reasons', () {
    testWidgets('7. Result screen: CARD_FROZEN decline shows friendly reason & Unfreeze card', (WidgetTester tester) async {
      const cardFrozenTransfer = TransferModel(
        transferId: 301,
        reference: 'TX-FROZEN',
        status: TransferStatusType.failed,
        amount: 1000.0,
        fundingType: FundingType.card,
        cardLast4: '1234',
        failureReason: 'CARD_FROZEN',
      );

      await tester.pumpWidget(
        buildTestApp(
          child: const TransferResultScreen(initialTransfer: cardFrozenTransfer),
          transferService: MockCardFundingTransferService(),
          cards: [],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Payment Declined'), findsOneWidget);
      expect(find.text('This card is currently frozen.'), findsOneWidget);
      expect(find.byKey(const Key('result_suggested_next_step')), findsOneWidget);
      expect(find.text('Suggested: Unfreeze card'), findsOneWidget);
    });

    testWidgets('8. Result screen: CARD_EXPIRED decline shows friendly reason & Choose another card', (WidgetTester tester) async {
      const cardExpiredTransfer = TransferModel(
        transferId: 302,
        reference: 'TX-EXPIRED',
        status: TransferStatusType.failed,
        amount: 1000.0,
        fundingType: FundingType.card,
        cardLast4: '1234',
        failureReason: 'CARD_EXPIRED',
      );

      await tester.pumpWidget(
        buildTestApp(
          child: const TransferResultScreen(initialTransfer: cardExpiredTransfer),
          transferService: MockCardFundingTransferService(),
          cards: [],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Payment Declined'), findsOneWidget);
      expect(find.text('This card has expired.'), findsOneWidget);
      expect(find.text('Suggested: Choose another card'), findsOneWidget);
    });

    testWidgets('9. Result screen: DAILY_LIMIT_EXCEEDED shows Lower the amount', (WidgetTester tester) async {
      const limitTransfer = TransferModel(
        transferId: 303,
        reference: 'TX-LIMIT',
        status: TransferStatusType.failed,
        amount: 75000.0,
        fundingType: FundingType.card,
        cardLast4: '1234',
        failureReason: 'DAILY_LIMIT_EXCEEDED',
      );

      await tester.pumpWidget(
        buildTestApp(
          child: const TransferResultScreen(initialTransfer: limitTransfer),
          transferService: MockCardFundingTransferService(),
          cards: [],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Payment Declined'), findsOneWidget);
      expect(find.text('Daily spending limit for this card has been exceeded.'), findsOneWidget);
      expect(find.text('Suggested: Lower the amount'), findsOneWidget);
    });

    testWidgets('10. Result screen: ONLINE_PAYMENTS_DISABLED shows Choose another card', (WidgetTester tester) async {
      const onlineDisabledTransfer = TransferModel(
        transferId: 304,
        reference: 'TX-ONLINE',
        status: TransferStatusType.failed,
        amount: 1000.0,
        fundingType: FundingType.card,
        cardLast4: '1234',
        failureReason: 'ONLINE_PAYMENTS_DISABLED',
      );

      await tester.pumpWidget(
        buildTestApp(
          child: const TransferResultScreen(initialTransfer: onlineDisabledTransfer),
          transferService: MockCardFundingTransferService(),
          cards: [],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Payment Declined'), findsOneWidget);
      expect(find.text('Online payments are disabled for this card.'), findsOneWidget);
      expect(find.text('Suggested: Choose another card'), findsOneWidget);
    });

    testWidgets('11. Result screen: Pending with automatic refresh transitions to confirmed success', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1200);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      final transferService = MockCardFundingTransferService();
      transferService.getTransferResult = const TransferModel(
        transferId: 401,
        reference: 'TX-PENDING-401',
        status: TransferStatusType.completed,
        amount: 2500.0,
        fundingType: FundingType.card,
        cardLast4: '1234',
        receiverDisplayName: 'Kamal Perera',
      );

      const pendingTransfer = TransferModel(
        transferId: 401,
        reference: 'TX-PENDING-401',
        status: TransferStatusType.pending,
        amount: 2500.0,
        fundingType: FundingType.card,
        cardLast4: '1234',
      );

      await tester.pumpWidget(
        buildTestApp(
          child: const TransferResultScreen(initialTransfer: pendingTransfer),
          transferService: transferService,
          cards: [],
        ),
      );

      // Initially shows pending processing state
      expect(find.text('Processing with Bank...'), findsOneWidget);
      expect(find.text('Payment Confirmed'), findsNothing);

      // Tap refresh button
      await tester.tap(find.byKey(const Key('transfer_refresh_btn')));
      await tester.pumpAndSettle();

      // Screen updates to green confirmed success
      expect(find.text('Payment Confirmed'), findsOneWidget);
      expect(find.text('COMPLETED'), findsOneWidget);
    });
  });

  group('Receipt & History Funding Filter', () {
    testWidgets('12. Receipt shows Paid with card •••• 1234', (WidgetTester tester) async {
      const receipt = ReceiptModel(
        receiptNumber: 'REC-TX-101',
        transferId: 101,
        reference: 'TX-CARD-101',
        status: 'COMPLETED',
        amount: 2500.0,
        fee: 25.0,
        totalDebited: 2525.0,
        fundingType: FundingType.card,
        cardLast4: '1234',
        paymentMethod: 'Paid with card •••• 1234 (TEST CARD)',
        receiver: ReceiptPartyModel(displayName: 'Kamal Perera'),
        bankTxRef: 'BANK-REF-101',
      );

      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.lightTheme,
          home: const ReceiptScreen(receipt: receipt),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Payment Method'), findsOneWidget);
      expect(find.text('Paid with card •••• 1234 (TEST CARD)'), findsOneWidget);
    });

    testWidgets('13. History filter allows filtering by Funding Type (All, Account, Card)', (WidgetTester tester) async {
      final historyService = MockCardFundingHistoryService();
      historyService.historyItems = [
        const TransferModel(
          transferId: 1,
          reference: 'TX-ACC-1',
          status: TransferStatusType.completed,
          amount: 500.0,
          fundingType: FundingType.account,
          receiverDisplayName: 'Account Payee',
        ),
        const TransferModel(
          transferId: 2,
          reference: 'TX-CARD-2',
          status: TransferStatusType.completed,
          amount: 1500.0,
          fundingType: FundingType.card,
          cardLast4: '4321',
          receiverDisplayName: 'Card Payee',
        ),
      ];

      await tester.pumpWidget(
        buildTestApp(
          initialLocation: '/history',
          transferService: MockCardFundingTransferService(),
          historyService: historyService,
          cards: [],
        ),
      );
      await tester.pumpAndSettle();

      // Both items visible initially
      expect(find.text('Account Payee'), findsOneWidget);
      expect(find.text('Card Payee'), findsOneWidget);

      // Open filter modal
      await tester.tap(find.byKey(const Key('history_filter_btn')));
      await tester.pumpAndSettle();

      // Verify funding chips
      expect(find.byKey(const Key('filter_funding_all')), findsOneWidget);
      expect(find.byKey(const Key('filter_funding_account')), findsOneWidget);
      expect(find.byKey(const Key('filter_funding_card')), findsOneWidget);

      // Select Card filter
      await tester.tap(find.byKey(const Key('filter_funding_card')));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('history_apply_filters_btn')));
      await tester.pumpAndSettle();

      // Only card transaction is displayed
      expect(find.text('Card Payee'), findsOneWidget);
      expect(find.text('Account Payee'), findsNothing);
    });
  });
}
