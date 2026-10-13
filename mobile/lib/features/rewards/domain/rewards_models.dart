import 'package:flutter/material.dart';

class RewardsSummaryModel {
  final int pointsBalance;
  final String tier;
  final String tierDisplayName;
  final int expiringSoonPoints;
  final String disclaimer;
  final int monthlyEarnedPoints;
  final int dailyEarnedPoints;
  final int dailyCapRemaining;
  final int monthlyCapRemaining;
  final bool hasNewOffers;

  const RewardsSummaryModel({
    required this.pointsBalance,
    required this.tier,
    required this.tierDisplayName,
    required this.expiringSoonPoints,
    required this.disclaimer,
    required this.monthlyEarnedPoints,
    required this.dailyEarnedPoints,
    required this.dailyCapRemaining,
    required this.monthlyCapRemaining,
    required this.hasNewOffers,
  });

  factory RewardsSummaryModel.fromJson(Map<String, dynamic> json) {
    return RewardsSummaryModel(
      pointsBalance: (json['pointsBalance'] as num?)?.toInt() ?? 0,
      tier: json['tier'] as String? ?? 'BASIC',
      tierDisplayName: json['tierDisplayName'] as String? ?? 'Basic',
      expiringSoonPoints: (json['expiringSoonPoints'] as num?)?.toInt() ?? 0,
      disclaimer: json['disclaimer'] as String? ?? 'Simulated points',
      monthlyEarnedPoints: (json['monthlyEarnedPoints'] as num?)?.toInt() ?? 0,
      dailyEarnedPoints: (json['dailyEarnedPoints'] as num?)?.toInt() ?? 0,
      dailyCapRemaining: (json['dailyCapRemaining'] as num?)?.toInt() ?? 500,
      monthlyCapRemaining: (json['monthlyCapRemaining'] as num?)?.toInt() ?? 5000,
      hasNewOffers: json['hasNewOffers'] as bool? ?? false,
    );
  }
}

class RewardOfferModel {
  final String id;
  final String title;
  final String description;
  final String merchantOrCategory;
  final double? pointsMultiplier;
  final int? fixedPoints;
  final DateTime? startAt;
  final DateTime? endAt;
  final int? maxPointsCap;
  final String terms;
  final bool active;

  const RewardOfferModel({
    required this.id,
    required this.title,
    required this.description,
    required this.merchantOrCategory,
    this.pointsMultiplier,
    this.fixedPoints,
    this.startAt,
    this.endAt,
    this.maxPointsCap,
    required this.terms,
    required this.active,
  });

  factory RewardOfferModel.fromJson(Map<String, dynamic> json) {
    return RewardOfferModel(
      id: json['id'] as String? ?? '',
      title: json['title'] as String? ?? '',
      description: json['description'] as String? ?? '',
      merchantOrCategory: json['merchantOrCategory'] as String? ?? '',
      pointsMultiplier: (json['pointsMultiplier'] as num?)?.toDouble(),
      fixedPoints: (json['fixedPoints'] as num?)?.toInt(),
      startAt: json['startAt'] != null ? DateTime.tryParse(json['startAt'].toString()) : null,
      endAt: json['endAt'] != null ? DateTime.tryParse(json['endAt'].toString()) : null,
      maxPointsCap: (json['maxPointsCap'] as num?)?.toInt(),
      terms: json['terms'] as String? ?? '',
      active: json['active'] as bool? ?? true,
    );
  }

  String get rewardBadgeText {
    if (pointsMultiplier != null && pointsMultiplier! > 1.0) {
      return '${pointsMultiplier!.toStringAsFixed(pointsMultiplier! % 1 == 0 ? 0 : 1)}x Points';
    }
    if (fixedPoints != null) {
      return '+$fixedPoints pts';
    }
    return 'Offer';
  }
}

class PointsLedgerEntryModel {
  final String id;
  final String type; // EARN, REDEEM, EXPIRE, REVERSE, ADJUST
  final int points;
  final int runningBalance;
  final String? sourceTransactionId;
  final String reason;
  final String? offerId;
  final DateTime? expiresAt;
  final DateTime createdAt;
  final String disclaimer;
  final Map<String, dynamic> metadata;

  const PointsLedgerEntryModel({
    required this.id,
    required this.type,
    required this.points,
    required this.runningBalance,
    this.sourceTransactionId,
    required this.reason,
    this.offerId,
    this.expiresAt,
    required this.createdAt,
    required this.disclaimer,
    required this.metadata,
  });

  factory PointsLedgerEntryModel.fromJson(Map<String, dynamic> json) {
    return PointsLedgerEntryModel(
      id: json['id'] as String? ?? '',
      type: json['type'] as String? ?? 'EARN',
      points: (json['points'] as num?)?.toInt() ?? 0,
      runningBalance: (json['runningBalance'] as num?)?.toInt() ?? 0,
      sourceTransactionId: json['sourceTransactionId'] as String?,
      reason: json['reason'] as String? ?? '',
      offerId: json['offerId'] as String?,
      expiresAt: json['expiresAt'] != null ? DateTime.tryParse(json['expiresAt'].toString()) : null,
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
      disclaimer: json['disclaimer'] as String? ?? 'Simulated points only',
      metadata: Map<String, dynamic>.from(json['metadata'] as Map? ?? {}),
    );
  }

  bool get isPositive => points > 0;
}

class RedeemPointsResponseModel {
  final String redemptionId;
  final String rewardType;
  final int pointsRedeemed;
  final int remainingBalance;
  final String? voucherCode;
  final String? partner;
  final String? statementCreditLabel;
  final String status;
  final DateTime redeemedAt;
  final String disclaimer;

  const RedeemPointsResponseModel({
    required this.redemptionId,
    required this.rewardType,
    required this.pointsRedeemed,
    required this.remainingBalance,
    this.voucherCode,
    this.partner,
    this.statementCreditLabel,
    required this.status,
    required this.redeemedAt,
    required this.disclaimer,
  });

  factory RedeemPointsResponseModel.fromJson(Map<String, dynamic> json) {
    return RedeemPointsResponseModel(
      redemptionId: json['redemptionId'] as String? ?? '',
      rewardType: json['rewardType'] as String? ?? 'VOUCHER',
      pointsRedeemed: (json['pointsRedeemed'] as num?)?.toInt() ?? 0,
      remainingBalance: (json['remainingBalance'] as num?)?.toInt() ?? 0,
      voucherCode: json['voucherCode'] as String?,
      partner: json['partner'] as String?,
      statementCreditLabel: json['statementCreditLabel'] as String?,
      status: json['status'] as String? ?? 'COMPLETED',
      redeemedAt: json['redeemedAt'] != null
          ? DateTime.tryParse(json['redeemedAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
      disclaimer: json['disclaimer'] as String? ?? 'Simulated points only',
    );
  }
}
