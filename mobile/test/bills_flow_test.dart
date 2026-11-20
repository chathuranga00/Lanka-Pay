import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:lankapay_id/core/test_keys.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/features/accounts/data/bank_account_service.dart';
import 'package:lankapay_id/features/accounts/domain/bank_account_model.dart';
import 'package:lankapay_id/features/cards/data/card_service.dart';
import 'package:lankapay_id/features/cards/domain/card_model.dart';
import 'package:lankapay_id/features/bills/data/bill_service.dart';
import 'package:lankapay_id/features/bills/domain/bill_models.dart';
import 'package:lankapay_id/features/bills/presentation/bills_screen.dart';
import 'package:lankapay_id/features/bills/presentation/bill_details_screen.dart';
import 'package:lankapay_id/features/bills/presentation/bill_result_screen.dart';
import 'package:lankapay_id/features/bills/presentation/bills_history_screen.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';

// -----------------------------------------------------------------------------
// Fake Services & Test Data
// -----------------------------------------------------------------------------

final testBillers = [
  const BillerModel(
    id: 'SIM_ELEC_01',
    name: 'Simulated Electricity Board',
    category: BillerCategory.electricity,
    referenceLabel: 'Account Number',
    serviceFee: 30.00,
    partialPayAllowed: true,
    requiresLookup: true,
  ),
  const BillerModel(
    id: 'SIM_WATER_01',
    name: 'Simulated Water Board',
    category: BillerCategory.water,
    referenceLabel: 'Account Number',
    serviceFee: 25.00,
    partialPayAllowed: false,
    requiresLookup: true,
  ),
  const BillerModel(
    id: 'SIM_MOBI_01',
    name: 'Simulated Mobile Operator A',
    category: BillerCategory.mobile,
    referenceLabel: 'Mobile Number',
    serviceFee: 0.00,
    partialPayAllowed: true,
    requiresLookup: false,
  ),
  const BillerModel(
    id: 'SIM_NET_01',
    name: 'Simulated Broadband',
    category: BillerCategory.internet,
    referenceLabel: 'Subscriber ID',
    serviceFee: 50.00,
    partialPayAllowed: false,
    requiresLookup: true,
  ),
];

final testSavedBillers = [
  const SavedBillerModel(
    id: 101,
    billerId: 'SIM_ELEC_01',
    billerName: 'Simulated Electricity Board',
    category: BillerCategory.electricity,
    nickname: 'Home Electricity',
    maskedReference: '******5678',
    createdAt: '2026-10-13T10:00:00Z',
  ),
  const SavedBillerModel(
    id: 102,
    billerId: 'SIM_WATER_01',
    billerName: 'Simulated Water Board',
    category: BillerCategory.water,
    nickname: 'Apartment Water',
    maskedReference: '******4321',
    createdAt: '2026-10-13T11:00:00Z',
  ),
];

final testBankAccounts = [
  const BankAccountModel(
    id: 1,
    institutionCode: 'BOC',
    accountName: 'Kamal Perera Savings',
    maskedNumber: '•••• 1234',
    status: 'ACTIVE',
    isDefault: true,
    discoverable: true,
    receivingEnabled: true,
  ),
];

final testCards = [
  const CardModel(
    id: 5,
    last4: '4242',
    expMonth: 12,
    expYear: 2028,
    brandLabel: 'Visa Platinum',
    nickname: 'Primary Debit Card',
  ),
];

class FakeBankAccountService implements BankAccountService {
  final List<BankAccountModel> accounts;

  FakeBankAccountService({List<BankAccountModel>? accounts})
      : accounts = accounts ?? testBankAccounts;

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

  FakeCardService({List<CardModel>? cards}) : cards = cards ?? testCards;

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

class FakeBillService extends BillService {
  final Future<BillLookupModel> Function(String, String)? onLookup;
  final Future<BillPaymentReceiptModel> Function()? onPay;

  FakeBillService({this.onLookup, this.onPay}) : super(Dio());

  @override
  Future<List<BillerModel>> listBillers({BillerCategory? category, String? query}) async {
    return testBillers.where((b) {
      if (category != null && b.category != category) return false;
      if (query != null && query.trim().isNotEmpty) {
        final q = query.trim().toLowerCase();
        return b.name.toLowerCase().contains(q) || b.id.toLowerCase().contains(q);
      }
      return true;
    }).toList();
  }

  @override
  Future<BillerModel> getBiller(String id) async {
    return testBillers.firstWhere((b) => b.id == id);
  }

  @override
  Future<List<SavedBillerModel>> listSavedBillers() async {
    return testSavedBillers;
  }

  @override
  Future<BillLookupModel> lookupBill({
    required String billerId,
    required String customerReference,
  }) async {
    if (onLookup != null) {
      return onLookup!(billerId, customerReference);
    }
    return const BillLookupModel(
      billerId: 'SIM_ELEC_01',
      billerName: 'Simulated Electricity Board',
      category: BillerCategory.electricity,
      customerReference: '1002345678',
      maskedReference: '******5678',
      customerName: 'K. A. Perera',
      amountDue: 2500.00,
      minimumAmount: 100.00,
      dueDate: '2026-10-31',
      billStatus: 'BILL_FOUND',
      statusMessage: 'Bill outstanding',
      serviceFee: 30.00,
      totalAmount: 2530.00,
    );
  }

  @override
  Future<BillPaymentReceiptModel> payBill({
    required String billerId,
    required String customerReference,
    required double amount,
    required String fundingType,
    required int fundingId,
    required String pin,
    String currency = 'LKR',
    String? note,
    bool saveBiller = false,
    String? billerNickname,
    String? idempotencyKey,
  }) async {
    if (onPay != null) {
      return onPay!();
    }
    return const BillPaymentReceiptModel(
      receiptNumber: 'RCP-20261013-1001',
      paymentId: 10,
      reference: 'PAY-REF-1001',
      billerId: 'SIM_ELEC_01',
      billerName: 'Simulated Electricity Board',
      category: BillerCategory.electricity,
      maskedReference: '******5678',
      customerName: 'K. A. Perera',
      amount: 2500.00,
      fee: 30.00,
      totalAmount: 2530.00,
      currency: 'LKR',
      status: 'COMPLETED',
      billerConfirmationRef: 'ELEC-CONF-9988',
      fundingType: 'ACCOUNT',
      fundingLabel: 'Kamal Perera Savings (•••• 1234)',
      billDueDate: '2026-10-31',
      timestamp: '2026-10-13 18:00:00',
    );
  }

  @override
  Future<List<BillPaymentReceiptModel>> listPayments({int offset = 0, int limit = 50}) async {
    return [
      const BillPaymentReceiptModel(
        receiptNumber: 'RCP-20261013-1001',
        paymentId: 10,
        reference: 'PAY-REF-1001',
        billerId: 'SIM_ELEC_01',
        billerName: 'Simulated Electricity Board',
        category: BillerCategory.electricity,
        maskedReference: '******5678',
        customerName: 'K. A. Perera',
        amount: 2500.00,
        fee: 30.00,
        totalAmount: 2530.00,
        currency: 'LKR',
        status: 'COMPLETED',
        fundingType: 'ACCOUNT',
      ),
      const BillPaymentReceiptModel(
        receiptNumber: 'RCP-20261013-1002',
        paymentId: 11,
        reference: 'PAY-REF-1002',
        billerId: 'SIM_WATER_01',
        billerName: 'Simulated Water Board',
        category: BillerCategory.water,
        maskedReference: '******4321',
        customerName: 'K. A. Perera',
        amount: 800.00,
        fee: 25.00,
        totalAmount: 825.00,
        currency: 'LKR',
        status: 'PENDING',
        fundingType: 'ACCOUNT',
      ),
      const BillPaymentReceiptModel(
        receiptNumber: 'RCP-20261013-1003',
        paymentId: 12,
        reference: 'PAY-REF-1003',
        billerId: 'SIM_MOBI_01',
        billerName: 'Simulated Mobile Operator A',
        category: BillerCategory.mobile,
        maskedReference: '******0123',
        customerName: 'K. A. Perera',
        amount: 500.00,
        fee: 0.00,
        totalAmount: 500.00,
        currency: 'LKR',
        status: 'FAILED',
        failureReason: 'Account suspended by biller',
        fundingType: 'CARD',
      ),
      const BillPaymentReceiptModel(
        receiptNumber: 'RCP-20261013-1004',
        paymentId: 13,
        reference: 'PAY-REF-1004',
        billerId: 'SIM_NET_01',
        billerName: 'Simulated Broadband',
        category: BillerCategory.internet,
        maskedReference: '******9999',
        customerName: 'K. A. Perera',
        amount: 1200.00,
        fee: 50.00,
        totalAmount: 1250.00,
        currency: 'LKR',
        status: 'REVERSED',
        fundingType: 'ACCOUNT',
      ),
    ];
  }
}

// -----------------------------------------------------------------------------
// Test App Wrapper
// -----------------------------------------------------------------------------

Widget createBillsTestApp({
  required Widget child,
  FakeBillService? fakeBillService,
}) {
  final service = fakeBillService ?? FakeBillService();

  return ProviderScope(
    overrides: [
      billServiceProvider.overrideWithValue(service),
      bankAccountServiceProvider.overrideWithValue(FakeBankAccountService()),
      cardServiceProvider.overrideWithValue(FakeCardService()),
    ],
    child: MaterialApp(
      theme: AppTheme.lightTheme,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [Locale('en')],
      home: child,
    ),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Bills Flow - BillsScreen', () {
    testWidgets('renders category grid, search field, saved billers, and biller list with keys', (tester) async {
      await tester.pumpWidget(createBillsTestApp(child: const BillsScreen()));
      await tester.pumpAndSettle();

      // Verify title & search
      expect(find.text('Pay Bills'), findsOneWidget);
      expect(find.byKey(AppTestKeys.billsSearch), findsOneWidget);

      // Verify category filter grid
      expect(find.byKey(AppTestKeys.billsCategoryGrid), findsOneWidget);
      expect(find.text('Electricity'), findsWidgets);
      expect(find.text('Water'), findsWidgets);
      expect(find.text('Mobile'), findsWidgets);

      // Verify saved billers section with keys
      expect(find.byKey(AppTestKeys.savedBillersList), findsOneWidget);
      expect(find.byKey(AppTestKeys.savedBiller(101)), findsOneWidget);
      expect(find.byKey(AppTestKeys.savedBiller(102)), findsOneWidget);
      expect(find.text('Home Electricity'), findsOneWidget);
      expect(find.text('Apartment Water'), findsOneWidget);

      // Verify biller list items with biller_<id> keys
      expect(find.byKey(AppTestKeys.biller('SIM_ELEC_01')), findsOneWidget);
      expect(find.text('Simulated Electricity Board'), findsWidgets);

      // Scroll down to reveal remaining billers
      await tester.drag(find.byType(CustomScrollView), const Offset(0, -300));
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.biller('SIM_WATER_01')), findsOneWidget);
      expect(find.byKey(AppTestKeys.biller('SIM_MOBI_01')), findsOneWidget);
      expect(find.byKey(AppTestKeys.biller('SIM_NET_01')), findsOneWidget);
    });

    testWidgets('filtering by category displays only matching billers', (tester) async {
      await tester.pumpWidget(createBillsTestApp(child: const BillsScreen()));
      await tester.pumpAndSettle();

      // Tap 'Water' chip
      await tester.tap(find.text('Water'));
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.biller('SIM_WATER_01')), findsOneWidget);
      expect(find.byKey(AppTestKeys.biller('SIM_ELEC_01')), findsNothing);
      expect(find.byKey(AppTestKeys.biller('SIM_MOBI_01')), findsNothing);
    });

    testWidgets('searching biller filters the list', (tester) async {
      await tester.pumpWidget(createBillsTestApp(child: const BillsScreen()));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(AppTestKeys.billsSearch), 'Broadband');
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.biller('SIM_NET_01')), findsOneWidget);
      expect(find.byKey(AppTestKeys.biller('SIM_ELEC_01')), findsNothing);
    });
  });

  group('Bills Flow - BillDetailsScreen', () {
    testWidgets('renders customer reference field, performs lookup, shows breakdown and pay button', (tester) async {
      await tester.pumpWidget(
        createBillsTestApp(
          child: BillDetailsScreen(biller: testBillers[0]),
        ),
      );
      await tester.pumpAndSettle();

      // Check fields
      expect(find.text('Simulated Electricity Board'), findsWidgets);
      expect(find.byKey(AppTestKeys.billReference), findsOneWidget);
      expect(find.byKey(AppTestKeys.billAmountField), findsOneWidget);
      expect(find.byKey(AppTestKeys.billPinField), findsOneWidget);
      expect(find.byKey(AppTestKeys.billSaveCheckbox), findsOneWidget);
      expect(find.byKey(AppTestKeys.billPay), findsOneWidget);

      // Enter customer reference & perform lookup
      await tester.enterText(find.byKey(AppTestKeys.billReference), '1002345678');
      await tester.tap(find.text('Lookup'));
      await tester.pumpAndSettle();

      // Verify bill found breakdown
      expect(find.text('Bill Found'), findsOneWidget);
      expect(find.text('Customer: K. A. Perera'), findsOneWidget);
      expect(find.text('Amount Due'), findsOneWidget);
      expect(find.text('Service Fee'), findsOneWidget);
      expect(find.text('Total Payable'), findsOneWidget);
      expect(find.text('LKR 2530.00'), findsWidgets);
    });

    testWidgets('handles NO_BILL_DUE lookup result properly', (tester) async {
      final fake = FakeBillService(
        onLookup: (bId, ref) async => const BillLookupModel(
          billerId: 'SIM_ELEC_01',
          billerName: 'Simulated Electricity Board',
          category: BillerCategory.electricity,
          customerReference: '1002345990',
          maskedReference: '******5990',
          customerName: 'K. A. Perera',
          amountDue: 0.00,
          minimumAmount: 0.00,
          billStatus: 'NO_BILL_DUE',
          statusMessage: 'No outstanding balance on this account',
          serviceFee: 30.00,
          totalAmount: 30.00,
        ),
      );

      await tester.pumpWidget(
        createBillsTestApp(
          child: BillDetailsScreen(biller: testBillers[0]),
          fakeBillService: fake,
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(AppTestKeys.billReference), '1002345990');
      await tester.tap(find.text('Lookup'));
      await tester.pumpAndSettle();

      expect(find.text('No Bill Due'), findsOneWidget);
      expect(find.text('No outstanding balance on this account'), findsOneWidget);
    });

    testWidgets('handles AMOUNT_CHANGED lookup result notice', (tester) async {
      final fake = FakeBillService(
        onLookup: (bId, ref) async => const BillLookupModel(
          billerId: 'SIM_ELEC_01',
          billerName: 'Simulated Electricity Board',
          category: BillerCategory.electricity,
          customerReference: '1002345991',
          maskedReference: '******5991',
          customerName: 'K. A. Perera',
          amountDue: 3850.00,
          minimumAmount: 100.00,
          dueDate: '2026-10-31',
          billStatus: 'AMOUNT_CHANGED',
          statusMessage: 'Biller revised invoice amount',
          serviceFee: 30.00,
          totalAmount: 3880.00,
        ),
      );

      await tester.pumpWidget(
        createBillsTestApp(
          child: BillDetailsScreen(biller: testBillers[0]),
          fakeBillService: fake,
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(AppTestKeys.billReference), '1002345991');
      await tester.tap(find.text('Lookup'));
      await tester.pumpAndSettle();

      expect(find.text('Bill Amount Updated'), findsOneWidget);
      expect(find.text('Biller revised invoice amount'), findsOneWidget);
      expect(find.text('LKR 3880.00'), findsWidgets);
    });

    testWidgets('validation error when trying to pay without PIN or reference', (tester) async {
      await tester.pumpWidget(
        createBillsTestApp(
          child: BillDetailsScreen(biller: testBillers[0]),
        ),
      );
      await tester.pumpAndSettle();

      // Scroll to pay button and tap
      await tester.ensureVisible(find.byKey(AppTestKeys.billPay));
      await tester.tap(find.byKey(AppTestKeys.billPay));
      await tester.pumpAndSettle();

      expect(find.text('Customer reference is required.'), findsOneWidget);
    });
  });

  group('Bills Flow - Result Screens (Distinct States)', () {
    testWidgets('renders COMPLETED success result screen correctly', (tester) async {
      const receipt = BillPaymentReceiptModel(
        receiptNumber: 'RCP-20261013-1001',
        paymentId: 10,
        reference: 'PAY-REF-1001',
        billerId: 'SIM_ELEC_01',
        billerName: 'Simulated Electricity Board',
        category: BillerCategory.electricity,
        maskedReference: '******5678',
        customerName: 'K. A. Perera',
        amount: 2500.00,
        fee: 30.00,
        totalAmount: 2530.00,
        currency: 'LKR',
        status: 'COMPLETED',
        billerConfirmationRef: 'ELEC-CONF-9988',
        fundingType: 'ACCOUNT',
        fundingLabel: 'BOC Savings (•••• 1234)',
      );

      await tester.pumpWidget(createBillsTestApp(child: const BillResultScreen(receipt: receipt)));
      await tester.pumpAndSettle();

      expect(find.text('Payment Successful'), findsOneWidget);
      expect(find.text('COMPLETED'), findsOneWidget);
      expect(find.text('LKR 2530.00'), findsOneWidget);
      expect(find.text('RCP-20261013-1001'), findsOneWidget);
      expect(find.text('ELEC-CONF-9988'), findsOneWidget);
      expect(find.byKey(AppTestKeys.billResultDone), findsWidgets);
    });

    testWidgets('renders PENDING confirmation result screen correctly', (tester) async {
      const receipt = BillPaymentReceiptModel(
        receiptNumber: 'RCP-20261013-1002',
        paymentId: 11,
        reference: 'PAY-REF-1002',
        billerId: 'SIM_WATER_01',
        billerName: 'Simulated Water Board',
        category: BillerCategory.water,
        maskedReference: '******4321',
        customerName: 'K. A. Perera',
        amount: 800.00,
        fee: 25.00,
        totalAmount: 825.00,
        currency: 'LKR',
        status: 'PENDING',
        fundingType: 'ACCOUNT',
      );

      await tester.pumpWidget(createBillsTestApp(child: const BillResultScreen(receipt: receipt)));
      await tester.pumpAndSettle();

      expect(find.text('Payment Pending'), findsOneWidget);
      expect(find.text('PENDING'), findsOneWidget);
      expect(find.text('LKR 825.00'), findsOneWidget);
    });

    testWidgets('renders FAILED result screen with retry button', (tester) async {
      const receipt = BillPaymentReceiptModel(
        receiptNumber: 'RCP-20261013-1003',
        paymentId: 12,
        reference: 'PAY-REF-1003',
        billerId: 'SIM_MOBI_01',
        billerName: 'Simulated Mobile Operator A',
        category: BillerCategory.mobile,
        maskedReference: '******0123',
        customerName: 'K. A. Perera',
        amount: 500.00,
        fee: 0.00,
        totalAmount: 500.00,
        currency: 'LKR',
        status: 'FAILED',
        failureReason: 'Account suspended by biller',
        fundingType: 'CARD',
      );

      await tester.pumpWidget(createBillsTestApp(child: const BillResultScreen(receipt: receipt)));
      await tester.pumpAndSettle();

      expect(find.text('Payment Failed'), findsOneWidget);
      expect(find.text('FAILED'), findsOneWidget);
      expect(find.text('Account suspended by biller'), findsOneWidget);
      expect(find.byKey(AppTestKeys.billResultRetry), findsOneWidget);
    });

    testWidgets('renders REVERSED result screen correctly', (tester) async {
      const receipt = BillPaymentReceiptModel(
        receiptNumber: 'RCP-20261013-1004',
        paymentId: 13,
        reference: 'PAY-REF-1004',
        billerId: 'SIM_NET_01',
        billerName: 'Simulated Broadband',
        category: BillerCategory.internet,
        maskedReference: '******9999',
        customerName: 'K. A. Perera',
        amount: 1200.00,
        fee: 50.00,
        totalAmount: 1250.00,
        currency: 'LKR',
        status: 'REVERSED',
        fundingType: 'ACCOUNT',
      );

      await tester.pumpWidget(createBillsTestApp(child: const BillResultScreen(receipt: receipt)));
      await tester.pumpAndSettle();

      expect(find.text('Payment Reversed'), findsOneWidget);
      expect(find.text('REVERSED'), findsOneWidget);
      expect(find.text('LKR 1250.00'), findsOneWidget);
    });
  });

  group('Bills Flow - BillsHistoryScreen', () {
    testWidgets('renders history list and filters by status', (tester) async {
      await tester.pumpWidget(createBillsTestApp(child: const BillsHistoryScreen()));
      await tester.pumpAndSettle();

      // Check title and history filter key
      expect(find.text('Bill Payments History'), findsOneWidget);
      expect(find.byKey(AppTestKeys.billHistoryFilter), findsOneWidget);

      // Verify payment items rendered
      expect(find.text('Simulated Electricity Board'), findsOneWidget);
      expect(find.text('Simulated Water Board'), findsOneWidget);
      expect(find.text('Simulated Mobile Operator A'), findsOneWidget);
      expect(find.text('Simulated Broadband'), findsOneWidget);

      // Tap 'Pending' filter
      await tester.tap(find.text('Pending'));
      await tester.pumpAndSettle();

      expect(find.text('Simulated Water Board'), findsOneWidget);
      expect(find.text('Simulated Electricity Board'), findsNothing);
      expect(find.text('Simulated Mobile Operator A'), findsNothing);

      // Tap 'Failed' filter
      await tester.tap(find.text('Failed'));
      await tester.pumpAndSettle();

      expect(find.text('Simulated Mobile Operator A'), findsOneWidget);
      expect(find.text('Simulated Water Board'), findsNothing);
    });
  });
}
