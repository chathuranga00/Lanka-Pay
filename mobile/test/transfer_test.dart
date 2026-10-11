import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/features/accounts/data/bank_account_service.dart';
import 'package:lankapay_id/features/accounts/domain/bank_account_model.dart';
import 'package:lankapay_id/features/send/data/transfer_service.dart';
import 'package:lankapay_id/features/send/domain/transfer_models.dart';
import 'package:lankapay_id/features/send/presentation/send_screen.dart';
import 'package:lankapay_id/features/send/presentation/transfer_result_screen.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';

// Fake TransferService for testing
class FakeTransferService implements TransferService {
  LookupResultModel? lookupResult;
  QuoteModel? quoteResult;
  TransferModel? initiateResult;
  TransferModel? getTransferResult;
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
  }) async {
    if (quoteResult != null) return quoteResult!;
    return QuoteModel(
      quoteReference: 'QUO-TEST-123',
      payeeId: 42,
      payeeDisplayName: 'Kamal Perera',
      maskedPhone: '+9477***1234',
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
  }) async {
    lastIdempotencyKey = idempotencyKey;
    lastPin = pin;
    if (initiateResult != null) return initiateResult!;
    return TransferModel(
      transferId: 99,
      reference: 'TX-REF-CONFIRMED-99',
      status: TransferStatusType.completed,
      amount: amount,
      currency: currency,
      receiverDisplayName: 'Kamal Perera',
      bankTxRef: 'SIM-TX-BANK-123',
      note: note,
    );
  }

  @override
  Future<TransferModel> getTransferById(int transferId) async {
    if (getTransferResult != null) return getTransferResult!;
    return TransferModel(
      transferId: transferId,
      reference: 'TX-REF-CONFIRMED-$transferId',
      status: TransferStatusType.completed,
      amount: 1500.0,
      currency: 'LKR',
    );
  }
}

// Fake BankAccountService
class FakeBankAccountService implements BankAccountService {
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
  group('Transfer Flow Tests', () {
    testWidgets('Recipient search handles friendly error states for NOT_FOUND and DISCOVERY_DISABLED', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final fakeService = FakeTransferService();
      fakeService.lookupResult = const LookupResultModel(resultType: 'DISCOVERY_DISABLED');

      await tester.pumpWidget(
        createTestableWidget(
          child: const SendScreen(),
          overrides: [
            transferServiceProvider.overrideWithValue(fakeService),
            bankAccountServiceProvider.overrideWithValue(FakeBankAccountService()),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('send_phone_field')), '0771234567');
      await tester.tap(find.byKey(const Key('send_lookup_btn')));
      await tester.pumpAndSettle();

      expect(
        find.text('The recipient has disabled phone-number discovery for privacy under PDPA.'),
        findsOneWidget,
      );

      // Test NOT_FOUND friendly error
      fakeService.lookupResult = const LookupResultModel(resultType: 'NOT_FOUND');
      await tester.tap(find.byKey(const Key('send_lookup_btn')));
      await tester.pumpAndSettle();

      expect(
        find.text('No LankaPay-ID account found for this mobile number. Make sure the number is registered.'),
        findsOneWidget,
      );
    });

    testWidgets('Happy path: Search -> Recipient verification -> Account selection -> Amount -> Review -> Pin Auth', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final fakeService = FakeTransferService();
      fakeService.lookupResult = const LookupResultModel(
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

      await tester.pumpWidget(
        createTestableWidget(
          child: const SendScreen(),
          overrides: [
            transferServiceProvider.overrideWithValue(fakeService),
            bankAccountServiceProvider.overrideWithValue(FakeBankAccountService()),
          ],
        ),
      );
      await tester.pumpAndSettle();

      // Step 0: Search
      await tester.enterText(find.byKey(const Key('send_phone_field')), '0771234567');
      await tester.tap(find.byKey(const Key('send_lookup_btn')));
      await tester.pumpAndSettle();

      // Step 1: Verified Payee Card & Inputs
      expect(find.text('Kamal Perera'), findsOneWidget);
      expect(find.text('Receiving: Simulated Bank A (****1001)'), findsOneWidget);

      // Enter amount and note
      await tester.enterText(find.byKey(const Key('send_amount_field')), '1500.00');
      await tester.enterText(find.byKey(const Key('send_note_field')), 'Dinner split');

      // Click Review Transfer
      await tester.tap(find.byKey(const Key('send_review_btn')));
      await tester.pumpAndSettle();

      // Confirmation Sheet details displayed
      expect(find.text('Confirm Payment Details'), findsOneWidget);
      expect(find.text('Recipient Name'), findsOneWidget);
      expect(find.text('Recipient Bank'), findsOneWidget);
      expect(find.text('Receiving Account'), findsOneWidget);
      expect(find.text('LKR 1500.00'), findsNWidgets(2)); // Amount and Total
      expect(find.text('Network Fee'), findsOneWidget);

      // Proceed to Step-Up PIN authentication
      await tester.tap(find.byKey(const Key('confirmation_continue_btn')));
      await tester.pumpAndSettle();

      expect(find.text('Step-Up Authentication'), findsOneWidget);
      await tester.enterText(find.byKey(const Key('transfer_pin_input')), '123456');

      await tester.tap(find.byKey(const Key('transfer_confirm_pin_btn')));
      await tester.pumpAndSettle();

      // Verify Idempotency-Key and PIN passed
      expect(fakeService.lastPin, '123456');
      expect(fakeService.lastIdempotencyKey, isNotNull);
      expect(fakeService.lastIdempotencyKey!.startsWith('IDEM-'), isTrue);
    });

    testWidgets('TransferResultScreen: Success shows green confirmed only when COMPLETED', (WidgetTester tester) async {
      const completedTransfer = TransferModel(
        transferId: 101,
        reference: 'TX-REF-101',
        status: TransferStatusType.completed,
        amount: 2500.0,
        receiverDisplayName: 'Nimal Silva',
        bankTxRef: 'SIM-BOC-999',
        note: 'Rent payment',
      );

      await tester.pumpWidget(
        createTestableWidget(
          child: const TransferResultScreen(initialTransfer: completedTransfer),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Payment Confirmed'), findsOneWidget);
      expect(find.text('Bank adapter confirmed debit and credit transfer.'), findsOneWidget);
      expect(find.text('LKR 2500.00'), findsOneWidget);
      expect(find.text('TX-REF-101'), findsOneWidget);
      expect(find.text('SIM-BOC-999'), findsOneWidget);
      expect(find.text('COMPLETED'), findsOneWidget);
      // Make sure Pending elements are not shown
      expect(find.text('Requires Bank Reconciliation'), findsNothing);
      expect(find.text('Payment Failed'), findsNothing);
    });

    testWidgets('TransferResultScreen: Pending/RequiresReconciliation shows amber and refresh button, NEVER green', (WidgetTester tester) async {
      const pendingTransfer = TransferModel(
        transferId: 102,
        reference: 'TX-REF-102',
        status: TransferStatusType.requiresReconciliation,
        amount: 3000.0,
        receiverDisplayName: 'Sunil Dias',
      );

      await tester.pumpWidget(
        createTestableWidget(
          child: const TransferResultScreen(
            initialTransfer: pendingTransfer,
            enableAutoPolling: false,
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Ensure distinct warning/amber state
      expect(find.text('Requires Bank Reconciliation'), findsOneWidget);
      expect(find.text('Transaction timeout occurred. Our background worker is reconciling the settlement with the bank. Do not retry.'), findsOneWidget);
      expect(find.text('REQUIRESRECONCILIATION'), findsOneWidget);
      expect(find.byKey(const Key('transfer_refresh_btn')), findsOneWidget);
      // Never confirmed
      expect(find.text('Payment Confirmed'), findsNothing);
    });

    testWidgets('TransferResultScreen: Failed shows red error and failure reason', (WidgetTester tester) async {
      const failedTransfer = TransferModel(
        transferId: 103,
        reference: 'TX-REF-103',
        status: TransferStatusType.failed,
        amount: 500.0,
        failureReason: 'Origin bank account has insufficient balance.',
      );

      await tester.pumpWidget(
        createTestableWidget(
          child: const TransferResultScreen(initialTransfer: failedTransfer),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Payment Failed'), findsOneWidget);
      expect(find.text('Origin bank account has insufficient balance.'), findsOneWidget);
      expect(find.text('FAILED'), findsOneWidget);
      expect(find.text('Payment Confirmed'), findsNothing);
      expect(find.byKey(const Key('transfer_refresh_btn')), findsNothing);
    });
  });
}
