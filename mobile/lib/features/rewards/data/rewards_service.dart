import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../domain/rewards_models.dart';

final rewardsServiceProvider = Provider<RewardsService>((ref) {
  final dio = ref.watch(dioProvider);
  return RewardsService(dio);
});

final rewardsSummaryProvider = FutureProvider<RewardsSummaryModel>((ref) async {
  final service = ref.watch(rewardsServiceProvider);
  return service.getRewardsSummary();
});

final rewardsOffersProvider = FutureProvider<List<RewardOfferModel>>((ref) async {
  final service = ref.watch(rewardsServiceProvider);
  return service.getOffers();
});

class LedgerTypeFilterNotifier extends Notifier<String> {
  @override
  String build() => 'ALL';

  void setFilter(String filter) => state = filter;
}

final rewardsLedgerTypeFilterProvider = NotifierProvider<LedgerTypeFilterNotifier, String>(LedgerTypeFilterNotifier.new);

final rewardsLedgerProvider = FutureProvider.family<List<PointsLedgerEntryModel>, String>((ref, typeFilter) async {
  final service = ref.watch(rewardsServiceProvider);
  return service.getLedger(type: typeFilter == 'ALL' ? null : typeFilter);
});

class RewardsService {
  final Dio _dio;

  RewardsService(this._dio);

  Future<RewardsSummaryModel> getRewardsSummary() async {
    try {
      final res = await _dio.get('/api/v1/rewards');
      return RewardsSummaryModel.fromJson(Map<String, dynamic>.from(res.data as Map));
    } catch (e) {
      // Fallback demo summary
      return const RewardsSummaryModel(
        pointsBalance: 1250,
        tier: 'PLUS',
        tierDisplayName: 'Plus',
        expiringSoonPoints: 120,
        disclaimer: 'Simulated points only. Points hold no monetary cash value.',
        monthlyEarnedPoints: 340,
        dailyEarnedPoints: 50,
        dailyCapRemaining: 450,
        monthlyCapRemaining: 4660,
        hasNewOffers: true,
      );
    }
  }

  Future<List<RewardOfferModel>> getOffers() async {
    try {
      final res = await _dio.get('/api/v1/rewards/offers');
      final list = res.data as List;
      return list.map((e) => RewardOfferModel.fromJson(Map<String, dynamic>.from(e as Map))).toList();
    } catch (e) {
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
          id: 'OFFER-QR-LANKAQR',
          title: 'LankaQR Merchant Bonus',
          description: 'Get 50 bonus points on LankaQR purchases.',
          merchantOrCategory: 'LANKAQR',
          fixedPoints: 50,
          terms: 'Simulated points only.',
          active: true,
        ),
      ];
    }
  }

  Future<List<PointsLedgerEntryModel>> getLedger({String? type}) async {
    try {
      final res = await _dio.get(
        '/api/v1/rewards/ledger',
        queryParameters: type != null ? {'type': type} : null,
      );
      final list = res.data as List;
      return list.map((e) => PointsLedgerEntryModel.fromJson(Map<String, dynamic>.from(e as Map))).toList();
    } catch (e) {
      return [
        PointsLedgerEntryModel(
          id: '1',
          type: 'EARN',
          points: 50,
          runningBalance: 1250,
          sourceTransactionId: 'TXN-001',
          reason: 'LankaQR Payment bonus',
          createdAt: DateTime.now().subtract(const Duration(hours: 3)),
          disclaimer: 'Simulated points only',
          metadata: {},
        ),
        PointsLedgerEntryModel(
          id: '2',
          type: 'EARN',
          points: 100,
          runningBalance: 1200,
          sourceTransactionId: 'TXN-002',
          reason: 'CEB Bill payment',
          createdAt: DateTime.now().subtract(const Duration(days: 2)),
          disclaimer: 'Simulated points only',
          metadata: {},
        ),
      ];
    }
  }

  Future<RedeemPointsResponseModel> redeemPoints({
    required String rewardType,
    required int points,
    String? voucherPartner,
    String? pin,
    String? idempotencyKey,
  }) async {
    final body = {
      'rewardType': rewardType,
      'points': points,
      if (voucherPartner != null) 'voucherPartner': voucherPartner,
      if (pin != null) 'pin': pin,
    };

    final headers = <String, dynamic>{};
    if (idempotencyKey != null) {
      headers['Idempotency-Key'] = idempotencyKey;
    }
    if (pin != null) {
      headers['X-Step-Up-Pin'] = pin;
    }

    final res = await _dio.post(
      '/api/v1/rewards/redeem',
      data: body,
      options: Options(headers: headers),
    );

    return RedeemPointsResponseModel.fromJson(Map<String, dynamic>.from(res.data as Map));
  }
}
