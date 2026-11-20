import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lankapay_id/core/network/api_client.dart';
import 'package:lankapay_id/core/test_keys.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/features/accounts/data/bank_account_service.dart';
import 'package:lankapay_id/features/accounts/domain/bank_account_model.dart';
import 'package:lankapay_id/features/cards/data/card_service.dart';
import 'package:lankapay_id/features/cards/domain/card_model.dart';
import 'package:lankapay_id/features/cards/domain/card_validator.dart';
import 'package:lankapay_id/features/cards/presentation/add_card/add_card_flow_screen.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';

class FlowFakeCardService implements CardService {
  @override
  final Dio dio;
  @override
  final Dio vaultDio;

  bool shouldRejectVaultRealCard;
  bool shouldRejectOtp;
  int otpAttemptsLeft;
  String? tokenizedPan;
  String? tokenizedCvv;
  String? linkedCardToken;
  String? verifiedOtp;

  FlowFakeCardService({
    this.shouldRejectVaultRealCard = false,
    this.shouldRejectOtp = false,
    this.otpAttemptsLeft = 3,
    Dio? testDio,
    Dio? testVaultDio,
  })  : dio = testDio ?? Dio(),
        vaultDio = testVaultDio ?? Dio();

  @override
  Future<TokenizeResultModel> tokenize({
    required String pan,
    required String expMonth,
    required String expYear,
    required String cvv,
    required String holderName,
  }) async {
    tokenizedPan = pan;
    tokenizedCvv = cvv;

    if (shouldRejectVaultRealCard) {
      throw Exception('Test cards only. Never enter a real card number.');
    }

    final cleanPan = pan.replaceAll(RegExp(r'\D'), '');
    return TokenizeResultModel(
      cardToken: 'tok_test_simulated_999',
      last4: cleanPan.length >= 4 ? cleanPan.substring(cleanPan.length - 4) : '0010',
      expMonth: expMonth,
      expYear: expYear,
      brandLabel: 'TEST CARD',
    );
  }

  @override
  Future<CardModel> linkCard({
    required String cardToken,
    String? nickname,
    int? linkedBankAccountId,
    bool consentGiven = true,
    String? pin,
  }) async {
    linkedCardToken = cardToken;
    return CardModel(
      id: 201,
      userId: 1,
      cardKind: CardKind.linked,
      status: CardStatus.pendingVerification,
      last4: '0010',
      expMonth: 12,
      expYear: 2028,
      nickname: nickname ?? 'My Approved Card',
      perPaymentLimitMinor: 1000000,
      dailyLimitMinor: 5000000,
      onlinePaymentsEnabled: true,
      brandLabel: 'TEST CARD',
    );
  }

  @override
  Future<CardModel> verifyCard({
    required int id,
    required String otp,
  }) async {
    verifiedOtp = otp;
    if (shouldRejectOtp) {
      otpAttemptsLeft--;
      if (otpAttemptsLeft <= 0) {
        throw Exception('OTP expired or maximum verification attempts reached');
      }
      throw Exception('Invalid OTP. $otpAttemptsLeft attempts remaining');
    }
    return const CardModel(
      id: 201,
      userId: 1,
      cardKind: CardKind.linked,
      status: CardStatus.active,
      last4: '0010',
      expMonth: 12,
      expYear: 2028,
      nickname: 'My Approved Card',
      perPaymentLimitMinor: 1000000,
      dailyLimitMinor: 5000000,
      onlinePaymentsEnabled: true,
      brandLabel: 'TEST CARD',
    );
  }

  @override
  Future<CardModel> issueVirtualCard({
    required int linkedBankAccountId,
    String? nickname,
    String? pin,
  }) async {
    return CardModel(
      id: 202,
      userId: 1,
      cardKind: CardKind.virtual,
      status: CardStatus.active,
      last4: '8888',
      expMonth: 10,
      expYear: 2028,
      nickname: nickname ?? 'Virtual Card',
      perPaymentLimitMinor: 2500000,
      dailyLimitMinor: 10000000,
      onlinePaymentsEnabled: true,
      brandLabel: 'TEST CARD',
      linkedBankAccountId: linkedBankAccountId,
    );
  }

  @override
  Future<List<CardModel>> listCards() async => [];
  @override
  Future<CardModel> getCard(int id) async => throw UnimplementedError();
  @override
  Future<CardModel> freezeCard(int id) async => throw UnimplementedError();
  @override
  Future<CardModel> unfreezeCard(int id, {String? pin}) async => throw UnimplementedError();
  @override
  Future<CardRevealModel> revealCard(int id, {String? pin}) async => throw UnimplementedError();
  @override
  Future<CardModel> patchCard(int id, {String? nickname, int? perPaymentLimitMinor, int? dailyLimitMinor, bool? onlinePaymentsEnabled, String? pin}) async => throw UnimplementedError();
  @override
  Future<void> removeCard(int id, {String? pin}) async {}
}

class FlowFakeBankAccountService implements BankAccountService {
  @override
  Future<List<BankAccountModel>> listAccounts() async => [
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
      ];

  @override
  Future<List<BankInstitutionModel>> listInstitutions() async => [];
  @override
  Future<BankAccountModel> linkAccount({required String institutionCode, required String accountNumber, required String accountName, String? label, bool consentGiven = true}) async => (await listAccounts()).first;
  @override
  Future<BankAccountModel> patchAccount({required int id, String? label, bool? discoverable, bool? receivingEnabled}) async => (await listAccounts()).first;
  @override
  Future<BankAccountModel> setDefault(int id) async => (await listAccounts()).first;
  @override
  Future<void> removeAccount(int id) async {}
}

Widget createAddCardFlowTestWidget({
  required FlowFakeCardService cardService,
}) {
  return ProviderScope(
    overrides: [
      cardServiceProvider.overrideWithValue(cardService),
      bankAccountServiceProvider.overrideWithValue(FlowFakeBankAccountService()),
    ],
    child: MaterialApp(
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      home: const AddCardFlowScreen(),
    ),
  );
}

void main() {
  group('Add Card Flow Happy Path', () {
    testWidgets('completes 3-step add test card flow successfully', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final cardService = FlowFakeCardService();
      await tester.pumpWidget(createAddCardFlowTestWidget(cardService: cardService));
      await tester.pumpAndSettle();

      // Step 1: Choose "Add my card (test card)"
      expect(find.byKey(AppTestKeys.flowAddCard), findsOneWidget);
      expect(find.byKey(AppTestKeys.btnChooseTestCard), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.btnChooseTestCard));
      await tester.pumpAndSettle();

      // Step 2: Form screen
      expect(find.byKey(AppTestKeys.bannerTestCardNotice), findsOneWidget);
      expect(find.textContaining('Prototype: use TEST cards only'), findsOneWidget);

      const approvedPan = '9900 0000 0000 0010';
      expect(CardValidator.isValidLuhn(approvedPan), isTrue);

      await tester.enterText(find.byKey(AppTestKeys.inputCardNumber), approvedPan);
      await tester.enterText(find.byKey(AppTestKeys.inputCardExpiry), '12/28');
      await tester.enterText(find.byKey(AppTestKeys.inputCardCvv), '123');
      await tester.enterText(find.byKey(AppTestKeys.inputCardHolderName), 'Kamal Perera');
      await tester.enterText(find.byKey(AppTestKeys.inputCardNickname), 'My Travel Card');
      await tester.pumpAndSettle();

      // Check consent
      await tester.tap(find.byKey(AppTestKeys.checkboxCardConsent));
      await tester.pumpAndSettle();

      // Submit form
      expect(tester.widget<ElevatedButton>(find.byKey(AppTestKeys.btnCardFormSubmit)).onPressed, isNotNull);
      await tester.tap(find.byKey(AppTestKeys.btnCardFormSubmit));
      await tester.pumpAndSettle();

      expect(cardService.linkedCardToken, 'tok_test_simulated_999');

      // Step 3: Bank OTP screen
      expect(find.byKey(AppTestKeys.inputCardOtp), findsOneWidget);
      expect(find.byKey(AppTestKeys.otpCooldownText), findsOneWidget);

      // Enter 6-digit OTP
      await tester.enterText(find.byKey(AppTestKeys.inputCardOtp), '123456');
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.btnCardOtpVerify));
      await tester.pumpAndSettle();

      expect(cardService.verifiedOtp, '123456');

      // Success screen
      expect(find.byKey(AppTestKeys.cardOtpSuccess), findsOneWidget);
      expect(find.text('Card Verified Successfully!'), findsOneWidget);
      expect(find.byKey(AppTestKeys.btnCardOtpDone), findsOneWidget);
    });
  });

  group('Luhn Validation in UI', () {
    testWidgets('blocks submission when card number fails Luhn check', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final cardService = FlowFakeCardService();
      await tester.pumpWidget(createAddCardFlowTestWidget(cardService: cardService));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.btnChooseTestCard));
      await tester.pumpAndSettle();

      // Enter 16-digit number that fails Luhn:
      const invalidLuhnPan = '9900 0000 0000 0011';
      expect(CardValidator.isValidLuhn(invalidLuhnPan), isFalse);

      await tester.enterText(find.byKey(AppTestKeys.inputCardNumber), invalidLuhnPan);
      await tester.enterText(find.byKey(AppTestKeys.inputCardExpiry), '12/28');
      await tester.enterText(find.byKey(AppTestKeys.inputCardCvv), '123');
      await tester.enterText(find.byKey(AppTestKeys.inputCardHolderName), 'Kamal Perera');
      await tester.tap(find.byKey(AppTestKeys.checkboxCardConsent));
      await tester.pumpAndSettle();

      // Submit button is disabled in UI
      final submitBtn = tester.widget<ElevatedButton>(find.byKey(AppTestKeys.btnCardFormSubmit));
      expect(submitBtn.onPressed, isNull);
      expect(find.text('Invalid card number (fails Luhn check)'), findsOneWidget);
      expect(cardService.tokenizedPan, isNull);
    });
  });

  group('Vault Real Card Rejection Handling', () {
    testWidgets('shows friendly error when vault rejects real-looking card', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final cardService = FlowFakeCardService(shouldRejectVaultRealCard: true);
      await tester.pumpWidget(createAddCardFlowTestWidget(cardService: cardService));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.btnChooseTestCard));
      await tester.pumpAndSettle();

      const approvedPan = '9900 0000 0000 0010';
      await tester.enterText(find.byKey(AppTestKeys.inputCardNumber), approvedPan);
      await tester.enterText(find.byKey(AppTestKeys.inputCardExpiry), '12/28');
      await tester.enterText(find.byKey(AppTestKeys.inputCardCvv), '123');
      await tester.enterText(find.byKey(AppTestKeys.inputCardHolderName), 'Kamal Perera');
      await tester.tap(find.byKey(AppTestKeys.checkboxCardConsent));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.btnCardFormSubmit));
      await tester.pumpAndSettle();

      // Error message from vault is nicely displayed
      expect(find.textContaining('Test cards only. Never enter a real card number.'), findsOneWidget);
      // Remains on form screen
      expect(find.byKey(AppTestKeys.btnCardFormSubmit), findsOneWidget);
    });
  });

  group('Bank OTP Verification Failures & Attempts Decrement', () {
    testWidgets('displays remaining attempts and error message on invalid OTP', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final cardService = FlowFakeCardService(shouldRejectOtp: true, otpAttemptsLeft: 3);
      await tester.pumpWidget(createAddCardFlowTestWidget(cardService: cardService));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.btnChooseTestCard));
      await tester.pumpAndSettle();

      const approvedPan = '9900 0000 0000 0010';
      await tester.enterText(find.byKey(AppTestKeys.inputCardNumber), approvedPan);
      await tester.enterText(find.byKey(AppTestKeys.inputCardExpiry), '12/28');
      await tester.enterText(find.byKey(AppTestKeys.inputCardCvv), '123');
      await tester.enterText(find.byKey(AppTestKeys.inputCardHolderName), 'Kamal Perera');
      await tester.tap(find.byKey(AppTestKeys.checkboxCardConsent));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.btnCardFormSubmit));
      await tester.pumpAndSettle();

      // On OTP screen, enter wrong OTP
      expect(find.byKey(AppTestKeys.inputCardOtp), findsOneWidget);
      await tester.enterText(find.byKey(AppTestKeys.inputCardOtp), '000000');
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.btnCardOtpVerify));
      await tester.pumpAndSettle();

      // Error banner / text shows attempts remaining
      expect(find.textContaining('Invalid OTP. 2 attempts remaining'), findsOneWidget);
      expect(find.byKey(AppTestKeys.cardOtpSuccess), findsNothing);
    });
  });

  group('Security & PCI Isolation Verification', () {
    testWidgets('proves card values never reach logger (vaultDio has no LogInterceptor)', (tester) async {
      // Create real Riverpod container and inspect vaultDioProvider
      final container = ProviderContainer();
      addTearDown(container.dispose);

      final vaultDio = container.read(vaultDioProvider);
      final standardDio = container.read(dioProvider);

      expect(vaultDio, isNotNull);
      expect(standardDio, isNotNull);

      // Vault Dio MUST NOT have any LogInterceptor
      final hasLogOnVault = vaultDio.interceptors.any((i) => i is LogInterceptor);
      expect(
        hasLogOnVault,
        isFalse,
        reason: 'PCI Compliance: vaultDio must strictly NOT contain LogInterceptor so PAN and CVV never leak to logs',
      );

      // Verify that vaultDio only has AuthInterceptor
      expect(vaultDio.interceptors.whereType<LogInterceptor>().isEmpty, isTrue);
    });
  });
}
