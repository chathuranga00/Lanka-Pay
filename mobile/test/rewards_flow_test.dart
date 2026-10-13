import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:lankapay_id/core/test_keys.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/features/rewards/data/rewards_service.dart';
import 'package:lankapay_id/features/rewards/domain/rewards_models.dart';
import 'package:lankapay_id/features/rewards/presentation/rewards_screen.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';

// -----------------------------------------------------------------------------
// Fake Rewards Service for Widget Tests
// -----------------------------------------------------------------------------
class FakeRewardsService extends RewardsService {
  RewardsSummaryModel? mockSummary;
  List<RewardOfferModel>? mockOffers;
  List<PointsLedgerEntryModel>? mockLedger;
  bool redeemCalled = false;
  int? lastRedeemedPoints;
  String? lastRewardType;
  String? lastPin;

  FakeRewardsService() : super(Dio());

  @override
  Future<RewardsSummaryModel> getRewardsSummary() async {
    if (mockSummary != null) return mockSummary!;
    return const RewardsSummaryModel(
      pointsBalance: 1250,
      tier: 'PLUS',
      tierDisplayName: 'Plus',
      expiringSoonPoints: 120,
      disclaimer: 'Simulated points only. No real cashback payout exists.',
      monthlyEarnedPoints: 340,
      dailyEarnedPoints: 50,
      dailyCapRemaining: 450,
      monthlyCapRemaining: 4660,
      hasNewOffers: true,
    );
  }

  @override
  Future<List<RewardOfferModel>> getOffers() async {
    if (mockOffers != null) return mockOffers!;
    return [
      const RewardOfferModel(
        id: 'OFFER-BILL-2X',
        title: '2x Points on Utility Bills',
        description: 'Earn double simulated points on utility bills.',
        merchantOrCategory: 'BILLS',
        pointsMultiplier: 2.0,
        terms: 'Simulated points only.',
        active: true,
      ),
      const RewardOfferModel(
        id: 'OFFER-QR-50',
        title: 'LankaQR Merchant Bonus',
        description: 'Get 50 bonus points on LankaQR purchases.',
        merchantOrCategory: 'LANKAQR',
        fixedPoints: 50,
        terms: 'Simulated points only.',
        active: true,
      ),
    ];
  }

  @override
  Future<List<PointsLedgerEntryModel>> getLedger({String? type}) async {
    if (mockLedger != null) return mockLedger!;
    return [
      PointsLedgerEntryModel(
        id: 'LED-1',
        type: 'EARN',
        points: 50,
        runningBalance: 1250,
        sourceTransactionId: 'TXN-QR-001',
        reason: 'LankaQR Payment bonus',
        createdAt: DateTime.now().subtract(const Duration(hours: 2)),
        disclaimer: 'Simulated points only',
        metadata: {},
      ),
      PointsLedgerEntryModel(
        id: 'LED-2',
        type: 'REDEEM',
        points: -100,
        runningBalance: 1200,
        sourceTransactionId: null,
        reason: 'Redeemed for Keells Super Voucher',
        createdAt: DateTime.now().subtract(const Duration(days: 1)),
        disclaimer: 'Simulated points only',
        metadata: {},
      ),
    ];
  }

  @override
  Future<RedeemPointsResponseModel> redeemPoints({
    required String rewardType,
    required int points,
    String? voucherPartner,
    String? pin,
    String? idempotencyKey,
  }) async {
    redeemCalled = true;
    lastRedeemedPoints = points;
    lastRewardType = rewardType;
    lastPin = pin;

    return RedeemPointsResponseModel(
      redemptionId: 'RED-999',
      rewardType: rewardType,
      pointsRedeemed: points,
      remainingBalance: 1150,
      voucherCode: 'VOUCH-TEST-1234',
      partner: voucherPartner ?? 'Keells Super',
      statementCreditLabel: rewardType == 'STATEMENT_CREDIT' ? 'SIMULATED Statement Credit' : null,
      status: 'COMPLETED',
      redeemedAt: DateTime.now(),
      disclaimer: 'Simulated points only',
    );
  }
}

Widget createRewardsTestApp({
  required Widget child,
  FakeRewardsService? fakeService,
}) {
  final service = fakeService ?? FakeRewardsService();
  return ProviderScope(
    overrides: [
      rewardsServiceProvider.overrideWithValue(service),
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
  group('Rewards Flow Tests', () {
    testWidgets('1. Rewards screen renders points balance pill, tier badge, expiring soon, and simulated points disclaimer', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      final fake = FakeRewardsService();
      await tester.pumpWidget(createRewardsTestApp(
        child: const RewardsScreen(),
        fakeService: fake,
      ));
      await tester.pumpAndSettle();

      // Check header and simulated disclaimer
      expect(find.text('Rewards & Points'), findsOneWidget);
      expect(find.textContaining('Simulated points only'), findsAtLeastNWidgets(1));

      // Check tier pill
      expect(find.byKey(AppTestKeys.rewardsTierBadge), findsOneWidget);
      expect(find.text('Plus Tier'), findsOneWidget);

      // Check expiring soon
      expect(find.byKey(AppTestKeys.rewardsExpiring), findsOneWidget);
      expect(find.text('120 pts expiring soon'), findsOneWidget);

      // Check points balance pill
      expect(find.byKey(AppTestKeys.rewardsPointsPill), findsOneWidget);
      expect(find.text('1,250 pts'), findsOneWidget);

      // Check redeem button
      expect(find.byKey(AppTestKeys.rewardsRedeem), findsOneWidget);
    });

    testWidgets('2. Displays offers list and opens offer details with key rewards_offer_<id>', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      final fake = FakeRewardsService();
      await tester.pumpWidget(createRewardsTestApp(
        child: const RewardsScreen(),
        fakeService: fake,
      ));
      await tester.pumpAndSettle();

      // Offers with key rewards_offer_<id>
      final offer1Finder = find.byKey(AppTestKeys.rewardsOffer('OFFER-BILL-2X'));
      final offer2Finder = find.byKey(AppTestKeys.rewardsOffer('OFFER-QR-50'));

      expect(offer1Finder, findsOneWidget);
      expect(offer2Finder, findsOneWidget);
      expect(find.text('2x Points on Utility Bills'), findsOneWidget);
      expect(find.text('LankaQR Merchant Bonus'), findsOneWidget);

      // Tap on offer to view details
      await tester.tap(offer1Finder);
      await tester.pumpAndSettle();

      expect(find.text('Terms & Conditions'), findsOneWidget);
      expect(find.text('Got it'), findsOneWidget);
      expect(find.textContaining('Simulated points'), findsAtLeastNWidgets(1));

      await tester.tap(find.text('Got it'));
      await tester.pumpAndSettle();
    });

    testWidgets('3. Switches to Points History ledger tab and displays entries with simulated points label', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      final fake = FakeRewardsService();
      await tester.pumpWidget(createRewardsTestApp(
        child: const RewardsScreen(),
        fakeService: fake,
      ));
      await tester.pumpAndSettle();

      // Tap ledger tab
      await tester.tap(find.byKey(AppTestKeys.rewardsLedger));
      await tester.pumpAndSettle();

      // Verify ledger entries
      expect(find.text('LankaQR Payment bonus'), findsOneWidget);
      expect(find.text('Redeemed for Keells Super Voucher'), findsOneWidget);
      expect(find.text('+50 pts'), findsOneWidget);
      expect(find.text('-100 pts'), findsOneWidget);
      expect(find.textContaining('Simulated points'), findsAtLeastNWidgets(1));
    });

    testWidgets('4. Redeem flow opens sheet, enters step-up PIN, and confirms with key rewards_redeem', (tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      final fake = FakeRewardsService();
      await tester.pumpWidget(createRewardsTestApp(
        child: const RewardsScreen(),
        fakeService: fake,
      ));
      await tester.pumpAndSettle();

      // Tap rewards_redeem
      await tester.tap(find.byKey(AppTestKeys.rewardsRedeem));
      await tester.pumpAndSettle();

      // Redeem sheet opens
      expect(find.text('Redeem Simulated Points'), findsOneWidget);
      expect(find.byKey(AppTestKeys.rewardsPinField), findsOneWidget);
      expect(find.byKey(AppTestKeys.rewardsRedeemConfirm), findsOneWidget);

      // Enter step-up PIN
      await tester.enterText(find.byKey(AppTestKeys.rewardsPinField), '1234');
      await tester.pumpAndSettle();

      // Tap confirm
      await tester.tap(find.byKey(AppTestKeys.rewardsRedeemConfirm));
      await tester.pumpAndSettle();

      // Verify redeem service was invoked
      expect(fake.redeemCalled, isTrue);
      expect(fake.lastRedeemedPoints, 100);
      expect(fake.lastPin, '1234');

      // Success dialog shown
      expect(find.text('Redemption Successful'), findsOneWidget);
      expect(find.text('Voucher Code: VOUCH-TEST-1234'), findsOneWidget);
    });
  });
}
