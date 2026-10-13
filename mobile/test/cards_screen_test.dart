import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lankapay_id/core/test_keys.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/features/accounts/data/bank_account_service.dart';
import 'package:lankapay_id/features/accounts/domain/bank_account_model.dart';
import 'package:lankapay_id/features/accounts/presentation/accounts_screen.dart';
import 'package:lankapay_id/features/cards/data/card_service.dart';
import 'package:lankapay_id/features/cards/domain/card_model.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';

class FakeCardService implements CardService {
  @override
  final Dio dio;
  @override
  final Dio vaultDio;

  List<CardModel> cards;
  bool shouldThrowList;
  CardRevealModel? revealResponse;
  int? lastFrozenId;
  int? lastUnfrozenId;
  String? lastUnfreezePin;
  int? lastRevealedId;
  String? lastRevealPin;
  int? lastRemovedId;
  String? lastPatchedNickname;
  int? lastPatchedPerPayment;
  int? lastPatchedDaily;
  bool? lastPatchedOnline;
  String? lastPatchedPin;

  FakeCardService({
    List<CardModel>? initialCards,
    this.shouldThrowList = false,
    Dio? testDio,
    Dio? testVaultDio,
  })  : dio = testDio ?? Dio(),
        vaultDio = testVaultDio ?? Dio(),
        cards = initialCards ?? [
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
          const CardModel(
            id: 102,
            userId: 1,
            cardKind: CardKind.linked,
            status: CardStatus.active,
            last4: '5678',
            expMonth: 8,
            expYear: 2027,
            nickname: 'Physical Debit',
            perPaymentLimitMinor: 5000000,
            dailyLimitMinor: 20000000,
            onlinePaymentsEnabled: true,
            brandLabel: 'TEST CARD',
            linkedBankAccountId: 2,
          ),
        ];

  @override
  Future<TokenizeResultModel> tokenize({
    required String pan,
    required String expMonth,
    required String expYear,
    required String cvv,
    required String holderName,
  }) async {
    return const TokenizeResultModel(
      cardToken: 'tok_test_abc123',
      last4: '1234',
      expMonth: '12',
      expYear: '28',
      brandLabel: 'TEST CARD',
    );
  }

  @override
  Future<List<CardModel>> listCards() async {
    if (shouldThrowList) {
      throw Exception('Network failure loading cards');
    }
    return cards.where((c) => c.status != CardStatus.removed).toList();
  }

  @override
  Future<CardModel> getCard(int id) async {
    return cards.firstWhere((c) => c.id == id);
  }

  @override
  Future<CardModel> linkCard({
    required String cardToken,
    String? nickname,
    int? linkedBankAccountId,
    bool consentGiven = true,
    String? pin,
  }) async {
    final newCard = CardModel(
      id: 103,
      userId: 1,
      cardKind: CardKind.linked,
      status: CardStatus.pendingVerification,
      last4: '9999',
      expMonth: 11,
      expYear: 2029,
      nickname: nickname ?? 'My Test Card',
      perPaymentLimitMinor: 1000000,
      dailyLimitMinor: 5000000,
      onlinePaymentsEnabled: true,
      brandLabel: 'TEST CARD',
      linkedBankAccountId: linkedBankAccountId,
    );
    cards.add(newCard);
    return newCard;
  }

  @override
  Future<CardModel> issueVirtualCard({
    required int linkedBankAccountId,
    String? nickname,
    String? pin,
  }) async {
    final newCard = CardModel(
      id: 104,
      userId: 1,
      cardKind: CardKind.virtual,
      status: CardStatus.active,
      last4: '8888',
      expMonth: 10,
      expYear: 2028,
      nickname: nickname ?? 'New Virtual Card',
      perPaymentLimitMinor: 2500000,
      dailyLimitMinor: 10000000,
      onlinePaymentsEnabled: true,
      brandLabel: 'TEST CARD',
      linkedBankAccountId: linkedBankAccountId,
    );
    cards.add(newCard);
    return newCard;
  }

  @override
  Future<CardModel> verifyCard({
    required int id,
    required String otp,
  }) async {
    final idx = cards.indexWhere((c) => c.id == id);
    if (idx != -1) {
      cards[idx] = cards[idx].copyWith(status: CardStatus.active);
      return cards[idx];
    }
    throw Exception('Card not found');
  }

  @override
  Future<CardModel> freezeCard(int id) async {
    lastFrozenId = id;
    final idx = cards.indexWhere((c) => c.id == id);
    if (idx != -1) {
      cards[idx] = cards[idx].copyWith(status: CardStatus.frozen);
      return cards[idx];
    }
    throw Exception('Card not found');
  }

  @override
  Future<CardModel> unfreezeCard(int id, {String? pin}) async {
    lastUnfrozenId = id;
    lastUnfreezePin = pin;
    final idx = cards.indexWhere((c) => c.id == id);
    if (idx != -1) {
      cards[idx] = cards[idx].copyWith(status: CardStatus.active);
      return cards[idx];
    }
    throw Exception('Card not found');
  }

  @override
  Future<CardRevealModel> revealCard(int id, {String? pin}) async {
    lastRevealedId = id;
    lastRevealPin = pin;
    return revealResponse ??
        const CardRevealModel(
          cardId: 101,
          last4: '1234',
          pan: '4000123456781234',
          cvv: '987',
          expMonth: '12',
          expYear: '2028',
          expiresInSeconds: 30,
        );
  }

  @override
  Future<CardModel> patchCard(
    int id, {
    String? nickname,
    int? perPaymentLimitMinor,
    int? dailyLimitMinor,
    bool? onlinePaymentsEnabled,
    String? pin,
  }) async {
    lastPatchedNickname = nickname;
    lastPatchedPerPayment = perPaymentLimitMinor;
    lastPatchedDaily = dailyLimitMinor;
    lastPatchedOnline = onlinePaymentsEnabled;
    lastPatchedPin = pin;

    final idx = cards.indexWhere((c) => c.id == id);
    if (idx != -1) {
      cards[idx] = cards[idx].copyWith(
        nickname: nickname ?? cards[idx].nickname,
        perPaymentLimitMinor: perPaymentLimitMinor ?? cards[idx].perPaymentLimitMinor,
        dailyLimitMinor: dailyLimitMinor ?? cards[idx].dailyLimitMinor,
        onlinePaymentsEnabled: onlinePaymentsEnabled ?? cards[idx].onlinePaymentsEnabled,
      );
      return cards[idx];
    }
    throw Exception('Card not found');
  }

  @override
  Future<void> removeCard(int id) async {
    lastRemovedId = id;
    cards.removeWhere((c) => c.id == id);
  }
}

class FakeBankAccountService implements BankAccountService {
  final List<BankAccountModel> accounts;

  FakeBankAccountService({List<BankAccountModel>? accounts})
      : accounts = accounts ?? [
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
  }) async => accounts.first;
  @override
  Future<BankAccountModel> patchAccount({
    required int id,
    String? label,
    bool? discoverable,
    bool? receivingEnabled,
  }) async => accounts.first;
  @override
  Future<BankAccountModel> setDefault(int id) async => accounts.first;
  @override
  Future<void> removeAccount(int id) async {}
}

Widget createCardsTestWidget({
  required FakeCardService cardService,
  BankAccountService? bankService,
}) {
  return ProviderScope(
    overrides: [
      cardServiceProvider.overrideWithValue(cardService),
      bankAccountServiceProvider.overrideWithValue(bankService ?? FakeBankAccountService()),
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
      home: const AccountsScreen(),
    ),
  );
}

void main() {
  group('Cards Segment & Header Display', () {
    testWidgets('switches to Cards segment and renders title, + button, and Available limit', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final cardService = FakeCardService();
      await tester.pumpWidget(createCardsTestWidget(cardService: cardService));
      await tester.pumpAndSettle();

      // Tap Cards segment
      expect(find.byKey(AppTestKeys.segCards), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.segCards));
      await tester.pumpAndSettle();

      // Title Cards with + button [cards_add]
      expect(find.text('Cards'), findsWidgets);
      expect(find.byKey(AppTestKeys.cardsAdd), findsOneWidget);

      // Available label with remaining daily limit: 100,000 LKR
      expect(find.text('Available'), findsOneWidget);
      expect(find.text('LKR 100,000.00'), findsOneWidget);

      // Card shows "TEST CARD" instead of network logo
      expect(find.text('TEST CARD'), findsWidgets);

      // Action row has all 4 buttons
      expect(find.byKey(AppTestKeys.btnAddCard), findsOneWidget);
      expect(find.byKey(AppTestKeys.btnFreezeCard), findsOneWidget);
      expect(find.byKey(AppTestKeys.btnCardDetails), findsOneWidget);
      expect(find.byKey(AppTestKeys.btnCardSettings), findsOneWidget);

      // "No transaction yet" empty state below actions
      expect(find.text('No transaction yet'), findsOneWidget);
    });
  });

  group('Eye Toggle Digit Masking & Lifecycle Background Reset', () {
    testWidgets('masks digits by default, reveals on eye tap, and auto-resets on background', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final cardService = FakeCardService();
      await tester.pumpWidget(createCardsTestWidget(cardService: cardService));
      await tester.pumpAndSettle();

      // Switch to Cards tab
      await tester.tap(find.byKey(AppTestKeys.segCards));
      await tester.pumpAndSettle();

      // Default masked: should contain bullet masking
      expect(find.text('•••• •••• •••• ••••'), findsWidgets);

      // Tap eye icon to toggle visibility
      expect(find.byKey(AppTestKeys.cardToggleVisibility), findsWidgets);
      await tester.tap(find.byKey(AppTestKeys.cardToggleVisibility).first);
      await tester.pumpAndSettle();

      // Unmasked should show last 4 digits (•••• •••• •••• 1234)
      expect(find.text('•••• •••• •••• 1234'), findsWidgets);

      // Simulate app backgrounding
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      await tester.pumpAndSettle();
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();

      // Automatically reset to fully masked
      expect(find.text('•••• •••• •••• ••••'), findsWidgets);
    });
  });

  group('Freeze / Unfreeze Card Flow', () {
    testWidgets('freezes card after confirmation dialog and unfreezes after step-up PIN', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final cardService = FakeCardService();
      await tester.pumpWidget(createCardsTestWidget(cardService: cardService));
      await tester.pumpAndSettle();

      // Switch to Cards tab
      await tester.tap(find.byKey(AppTestKeys.segCards));
      await tester.pumpAndSettle();

      // Freeze button
      expect(find.byKey(AppTestKeys.btnFreezeCard), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.btnFreezeCard));
      await tester.pumpAndSettle();

      // Confirm freeze dialog
      expect(find.text('Freeze Card?'), findsOneWidget);
      expect(find.byKey(AppTestKeys.confirmFreezeCardBtn), findsOneWidget);
      await tester.tap(find.byKey(AppTestKeys.confirmFreezeCardBtn));
      await tester.pumpAndSettle();

      expect(cardService.lastFrozenId, 101);
      // Card is now frozen, button changes label to "Unfreeze"
      expect(find.text('Unfreeze'), findsOneWidget);
      expect(find.text('FROZEN'), findsWidgets);

      // Tap Unfreeze -> step-up PIN dialog required
      await tester.tap(find.byKey(AppTestKeys.btnFreezeCard));
      await tester.pumpAndSettle();

      expect(find.text('Unfreeze Card'), findsOneWidget);
      expect(find.byKey(AppTestKeys.stepUpPinInput), findsOneWidget);

      // Enter 6-digit PIN
      await tester.enterText(find.byKey(AppTestKeys.stepUpPinInput), '123456');
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.stepUpPinConfirmBtn));
      await tester.pumpAndSettle();

      expect(cardService.lastUnfrozenId, 101);
      expect(cardService.lastUnfreezePin, '123456');
      expect(find.text('Freeze'), findsOneWidget);
    });
  });

  group('Card Details Reveal with Step-Up PIN & 30s Countdown Timer', () {
    testWidgets('reveals virtual card details only after step-up and auto-hides after timer', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final cardService = FakeCardService();
      await tester.pumpWidget(createCardsTestWidget(cardService: cardService));
      await tester.pumpAndSettle();

      // Switch to Cards tab
      await tester.tap(find.byKey(AppTestKeys.segCards));
      await tester.pumpAndSettle();

      // Selected card is Virtual Daily (id: 101)
      await tester.tap(find.byKey(AppTestKeys.btnCardDetails));
      await tester.pumpAndSettle();

      // Step-up PIN dialog prompt
      expect(find.text('Reveal Card Details'), findsOneWidget);
      await tester.enterText(find.byKey(AppTestKeys.stepUpPinInput), '654321');
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(AppTestKeys.stepUpPinConfirmBtn));
      await tester.pumpAndSettle();

      expect(cardService.lastRevealedId, 101);
      expect(cardService.lastRevealPin, '654321');

      // Card details sheet is open
      expect(find.byKey(AppTestKeys.cardRevealSheet), findsOneWidget);
      expect(find.byKey(AppTestKeys.revealCountdownTimer), findsOneWidget);
      expect(find.text('4000 1234 5678 1234'), findsOneWidget);
      expect(find.text('987'), findsOneWidget);

      // Advance clock by 31 seconds -> auto closes
      await tester.pump(const Duration(seconds: 31));
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.cardRevealSheet), findsNothing);
    });

    testWidgets('refuses details for linked physical card', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      // Start with linked physical card first
      final cardService = FakeCardService(initialCards: [
        const CardModel(
          id: 102,
          userId: 1,
          cardKind: CardKind.linked,
          status: CardStatus.active,
          last4: '5678',
          expMonth: 8,
          expYear: 2027,
          nickname: 'Physical Debit',
          perPaymentLimitMinor: 5000000,
          dailyLimitMinor: 20000000,
          onlinePaymentsEnabled: true,
          brandLabel: 'TEST CARD',
        ),
      ]);

      await tester.pumpWidget(createCardsTestWidget(cardService: cardService));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.segCards));
      await tester.pumpAndSettle();

      // Tap Details button
      await tester.tap(find.byKey(AppTestKeys.btnCardDetails));
      await tester.pumpAndSettle();

      // Step-up PIN is NOT prompted, refuse message is shown
      expect(find.byKey(AppTestKeys.stepUpPinInput), findsNothing);
      expect(find.text('Linked Card Details'), findsOneWidget);
      expect(find.textContaining('Card details are never shown for linked physical cards'), findsOneWidget);
    });
  });

  group('Card Settings & Removal', () {
    testWidgets('settings sheet opens and allows removal with confirmation dialog', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final cardService = FakeCardService();
      await tester.pumpWidget(createCardsTestWidget(cardService: cardService));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.segCards));
      await tester.pumpAndSettle();

      // Open settings
      await tester.tap(find.byKey(AppTestKeys.btnCardSettings));
      await tester.pumpAndSettle();

      expect(find.byKey(AppTestKeys.cardSettingsSheet), findsOneWidget);
      expect(find.byKey(AppTestKeys.cardSettingsNicknameField), findsOneWidget);
      expect(find.byKey(AppTestKeys.cardSettingsRemoveBtn), findsOneWidget);

      // Tap Remove Card
      await tester.tap(find.byKey(AppTestKeys.cardSettingsRemoveBtn));
      await tester.pumpAndSettle();

      // Confirm dialog appears
      expect(find.text('Remove Card'), findsWidgets);
      expect(find.byKey(AppTestKeys.confirmRemoveCardBtn), findsOneWidget);

      await tester.tap(find.byKey(AppTestKeys.confirmRemoveCardBtn));
      await tester.pumpAndSettle();

      expect(cardService.lastRemovedId, 101);
    });
  });

  group('Empty & Error States', () {
    testWidgets('renders empty state when user has no cards', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final cardService = FakeCardService(initialCards: []);
      await tester.pumpWidget(createCardsTestWidget(cardService: cardService));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.segCards));
      await tester.pumpAndSettle();

      expect(find.text('Add your first card'), findsWidgets);
      expect(find.byKey(AppTestKeys.emptyAddCardBtn), findsOneWidget);
    });

    testWidgets('renders error state with retry on network failure', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      final cardService = FakeCardService(shouldThrowList: true);
      await tester.pumpWidget(createCardsTestWidget(cardService: cardService));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(AppTestKeys.segCards));
      await tester.pumpAndSettle();

      expect(find.text('Failed to load cards'), findsOneWidget);
      expect(find.byKey(AppTestKeys.btnRetry), findsOneWidget);

      // Now fix network and tap retry
      cardService.shouldThrowList = false;
      await tester.tap(find.byKey(AppTestKeys.btnRetry));
      await tester.pumpAndSettle();

      expect(find.text('Virtual Daily'), findsOneWidget);
    });
  });
}
