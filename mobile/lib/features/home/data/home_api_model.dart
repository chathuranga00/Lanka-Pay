/// Models for the GET /api/v1/home aggregated response and sections.

class HomeSectionModel<T> {
  final String status; // 'OK', 'FAILED', 'UNAVAILABLE'
  final T? data;
  final String? error;

  const HomeSectionModel({
    required this.status,
    this.data,
    this.error,
  });

  bool get isOk => status == 'OK';
  bool get isFailed => status == 'FAILED';
  bool get isUnavailable => status == 'UNAVAILABLE';

  factory HomeSectionModel.fromJson(
    Map<String, dynamic>? json,
    T Function(dynamic)? fromDataJson,
  ) {
    if (json == null) {
      return HomeSectionModel<T>(status: 'UNAVAILABLE', error: 'NULL_SECTION');
    }
    final status = json['status'] as String? ?? 'UNAVAILABLE';
    final error = json['error'] as String?;
    final rawData = json['data'];
    T? data;
    if (rawData != null && fromDataJson != null && status == 'OK') {
      try {
        data = fromDataJson(rawData);
      } catch (_) {
        data = null;
      }
    }
    return HomeSectionModel<T>(status: status, data: data, error: error);
  }
}

class GreetingDataModel {
  final String fullName;
  final String initial;
  final String salutation;

  const GreetingDataModel({
    required this.fullName,
    required this.initial,
    required this.salutation,
  });

  factory GreetingDataModel.fromJson(Map<String, dynamic> json) {
    return GreetingDataModel(
      fullName: json['fullName'] as String? ?? 'User',
      initial: json['initial'] as String? ?? 'U',
      salutation: json['salutation'] as String? ?? 'Good day',
    );
  }
}

class NotificationDataModel {
  final int unreadCount;

  const NotificationDataModel({required this.unreadCount});

  factory NotificationDataModel.fromJson(Map<String, dynamic> json) {
    return NotificationDataModel(
      unreadCount: (json['unreadCount'] as num?)?.toInt() ?? 0,
    );
  }
}

class LinkedAccountSummaryModel {
  final int id;
  final String bankCode;
  final String bankName;
  final String maskedAccountNumber;
  final String accountName;
  final bool isDefault;

  const LinkedAccountSummaryModel({
    required this.id,
    required this.bankCode,
    required this.bankName,
    required this.maskedAccountNumber,
    required this.accountName,
    required this.isDefault,
  });

  factory LinkedAccountSummaryModel.fromJson(Map<String, dynamic> json) {
    return LinkedAccountSummaryModel(
      id: (json['id'] as num?)?.toInt() ?? 0,
      bankCode: json['bankCode'] as String? ?? '',
      bankName: json['bankName'] as String? ?? '',
      maskedAccountNumber: json['maskedAccountNumber'] as String? ?? '',
      accountName: json['accountName'] as String? ?? '',
      isDefault: json['isDefault'] as bool? ?? false,
    );
  }
}

class CardSummaryModel {
  final int cardId;
  final String maskedNumber;
  final String brandLabel;
  final bool frozen;
  final String expiry;

  const CardSummaryModel({
    required this.cardId,
    required this.maskedNumber,
    required this.brandLabel,
    required this.frozen,
    required this.expiry,
  });

  factory CardSummaryModel.fromJson(Map<String, dynamic> json) {
    return CardSummaryModel(
      cardId: (json['cardId'] as num?)?.toInt() ?? 0,
      maskedNumber: json['maskedNumber'] as String? ?? '',
      brandLabel: json['brandLabel'] as String? ?? 'TEST CARD',
      frozen: json['frozen'] as bool? ?? false,
      expiry: json['expiry'] as String? ?? '12/28',
    );
  }
}

class WalletDataModel {
  final int primaryBalanceMinor;
  final String primaryBalanceLkr;
  final String currency;
  final bool isSimulated;
  final LinkedAccountSummaryModel? defaultAccount;
  final CardSummaryModel? activeCard;
  final int totalAccounts;
  final int totalCards;

  const WalletDataModel({
    required this.primaryBalanceMinor,
    required this.primaryBalanceLkr,
    required this.currency,
    required this.isSimulated,
    this.defaultAccount,
    this.activeCard,
    required this.totalAccounts,
    required this.totalCards,
  });

  factory WalletDataModel.fromJson(Map<String, dynamic> json) {
    return WalletDataModel(
      primaryBalanceMinor: (json['primaryBalanceMinor'] as num?)?.toInt() ?? 0,
      primaryBalanceLkr: json['primaryBalanceLkr'] as String? ?? '0.00',
      currency: json['currency'] as String? ?? 'LKR',
      isSimulated: json['isSimulated'] as bool? ?? true,
      defaultAccount: json['defaultAccount'] != null
          ? LinkedAccountSummaryModel.fromJson(json['defaultAccount'] as Map<String, dynamic>)
          : null,
      activeCard: json['activeCard'] != null
          ? CardSummaryModel.fromJson(json['activeCard'] as Map<String, dynamic>)
          : null,
      totalAccounts: (json['totalAccounts'] as num?)?.toInt() ?? 0,
      totalCards: (json['totalCards'] as num?)?.toInt() ?? 0,
    );
  }
}

class RecentActivityItemModel {
  final int id;
  final String reference;
  final int amountMinor;
  final String amountLkr;
  final String currency;
  final String status;
  final String direction;
  final String counterpartyName;
  final String? createdAt;
  final bool simulated;

  const RecentActivityItemModel({
    required this.id,
    required this.reference,
    required this.amountMinor,
    required this.amountLkr,
    required this.currency,
    required this.status,
    required this.direction,
    required this.counterpartyName,
    this.createdAt,
    required this.simulated,
  });

  factory RecentActivityItemModel.fromJson(Map<String, dynamic> json) {
    return RecentActivityItemModel(
      id: (json['id'] as num?)?.toInt() ?? 0,
      reference: json['reference'] as String? ?? '',
      amountMinor: (json['amountMinor'] as num?)?.toInt() ?? 0,
      amountLkr: json['amountLkr'] as String? ?? '0.00',
      currency: json['currency'] as String? ?? 'LKR',
      status: json['status'] as String? ?? 'COMPLETED',
      direction: json['direction'] as String? ?? 'OUTGOING',
      counterpartyName: json['counterpartyName'] as String? ?? 'Counterparty',
      createdAt: json['createdAt'] as String?,
      simulated: json['simulated'] as bool? ?? true,
    );
  }
}

class RewardsPointsDataModel {
  final int pointsBalance;
  final String formattedPoints;
  final bool hasNewOffers;

  const RewardsPointsDataModel({
    required this.pointsBalance,
    required this.formattedPoints,
    required this.hasNewOffers,
  });

  factory RewardsPointsDataModel.fromJson(Map<String, dynamic> json) {
    return RewardsPointsDataModel(
      pointsBalance: (json['pointsBalance'] as num?)?.toInt() ?? 0,
      formattedPoints: json['formattedPoints'] as String? ?? '0 pts',
      hasNewOffers: json['hasNewOffers'] as bool? ?? false,
    );
  }
}

class SpendSummaryDataModel {
  final int totalSpentMinor;
  final String totalSpentLkr;
  final int budgetMinor;
  final String budgetLkr;
  final double budgetUsedFraction;
  final String month;
  final bool hasTransactions;

  const SpendSummaryDataModel({
    required this.totalSpentMinor,
    required this.totalSpentLkr,
    required this.budgetMinor,
    required this.budgetLkr,
    required this.budgetUsedFraction,
    required this.month,
    required this.hasTransactions,
  });

  factory SpendSummaryDataModel.fromJson(Map<String, dynamic> json) {
    return SpendSummaryDataModel(
      totalSpentMinor: (json['totalSpentMinor'] as num?)?.toInt() ?? 0,
      totalSpentLkr: json['totalSpentLkr'] as String? ?? '0.00',
      budgetMinor: (json['budgetMinor'] as num?)?.toInt() ?? 0,
      budgetLkr: json['budgetLkr'] as String? ?? '0.00',
      budgetUsedFraction: (json['budgetUsedFraction'] as num?)?.toDouble() ?? 0.0,
      month: json['month'] as String? ?? 'Month',
      hasTransactions: json['hasTransactions'] as bool? ?? false,
    );
  }
}

class NudgeDataModel {
  final String nudgeId;
  final String type;
  final String message;
  final String actionLabel;
  final String actionRoute;

  const NudgeDataModel({
    required this.nudgeId,
    required this.type,
    required this.message,
    required this.actionLabel,
    required this.actionRoute,
  });

  factory NudgeDataModel.fromJson(Map<String, dynamic> json) {
    return NudgeDataModel(
      nudgeId: json['nudgeId'] as String? ?? '',
      type: json['type'] as String? ?? '',
      message: json['message'] as String? ?? '',
      actionLabel: json['actionLabel'] as String? ?? '',
      actionRoute: json['actionRoute'] as String? ?? '',
    );
  }
}

class ShortcutItemModel {
  final String id;
  final String title;
  final String icon;
  final String route;
  final bool enabled;

  const ShortcutItemModel({
    required this.id,
    required this.title,
    required this.icon,
    required this.route,
    required this.enabled,
  });

  factory ShortcutItemModel.fromJson(Map<String, dynamic> json) {
    return ShortcutItemModel(
      id: json['id'] as String? ?? '',
      title: json['title'] as String? ?? '',
      icon: json['icon'] as String? ?? '',
      route: json['route'] as String? ?? '',
      enabled: json['enabled'] as bool? ?? true,
    );
  }
}

class HomeAggregateResponseModel {
  final HomeSectionModel<GreetingDataModel> greeting;
  final HomeSectionModel<NotificationDataModel> unreadNotifications;
  final HomeSectionModel<WalletDataModel> wallet;
  final HomeSectionModel<List<RecentActivityItemModel>> recentActivity;
  final HomeSectionModel<RewardsPointsDataModel> rewardsPoints;
  final HomeSectionModel<dynamic> stories;
  final HomeSectionModel<SpendSummaryDataModel> spendSummary;
  final HomeSectionModel<NudgeDataModel> nudge;
  final HomeSectionModel<List<ShortcutItemModel>> shortcuts;

  const HomeAggregateResponseModel({
    required this.greeting,
    required this.unreadNotifications,
    required this.wallet,
    required this.recentActivity,
    required this.rewardsPoints,
    required this.stories,
    required this.spendSummary,
    required this.nudge,
    required this.shortcuts,
  });

  factory HomeAggregateResponseModel.fromJson(Map<String, dynamic> json) {
    return HomeAggregateResponseModel(
      greeting: HomeSectionModel.fromJson(
        json['greeting'] as Map<String, dynamic>?,
        (d) => GreetingDataModel.fromJson(d as Map<String, dynamic>),
      ),
      unreadNotifications: HomeSectionModel.fromJson(
        json['unreadNotifications'] as Map<String, dynamic>?,
        (d) => NotificationDataModel.fromJson(d as Map<String, dynamic>),
      ),
      wallet: HomeSectionModel.fromJson(
        json['wallet'] as Map<String, dynamic>?,
        (d) => WalletDataModel.fromJson(d as Map<String, dynamic>),
      ),
      recentActivity: HomeSectionModel.fromJson(
        json['recentActivity'] as Map<String, dynamic>?,
        (d) => (d as List)
            .map((item) => RecentActivityItemModel.fromJson(item as Map<String, dynamic>))
            .toList(),
      ),
      rewardsPoints: HomeSectionModel.fromJson(
        json['rewardsPoints'] as Map<String, dynamic>?,
        (d) => RewardsPointsDataModel.fromJson(d as Map<String, dynamic>),
      ),
      stories: HomeSectionModel.fromJson(
        json['stories'] as Map<String, dynamic>?,
        (d) => d,
      ),
      spendSummary: HomeSectionModel.fromJson(
        json['spendSummary'] as Map<String, dynamic>?,
        (d) => SpendSummaryDataModel.fromJson(d as Map<String, dynamic>),
      ),
      nudge: HomeSectionModel.fromJson(
        json['nudge'] as Map<String, dynamic>?,
        (d) => NudgeDataModel.fromJson(d as Map<String, dynamic>),
      ),
      shortcuts: HomeSectionModel.fromJson(
        json['shortcuts'] as Map<String, dynamic>?,
        (d) => (d as List)
            .map((item) => ShortcutItemModel.fromJson(item as Map<String, dynamic>))
            .toList(),
      ),
    );
  }
}
