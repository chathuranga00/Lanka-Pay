class AllowanceScheduleModel {
  final bool enabled;
  final int amountMinor;
  final String frequency;
  final String? nextRunDate;
  final String? sourceAccount;

  const AllowanceScheduleModel({
    required this.enabled,
    required this.amountMinor,
    required this.frequency,
    this.nextRunDate,
    this.sourceAccount,
  });

  factory AllowanceScheduleModel.fromJson(Map<String, dynamic> json) {
    return AllowanceScheduleModel(
      enabled: json['enabled'] as bool? ?? false,
      amountMinor: (json['amountMinor'] as num?)?.toInt() ?? 0,
      frequency: json['frequency'] as String? ?? 'WEEKLY',
      nextRunDate: json['nextRunDate'] as String?,
      sourceAccount: json['sourceAccount'] as String?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'enabled': enabled,
      'amountMinor': amountMinor,
      'frequency': frequency,
      'nextRunDate': nextRunDate,
      'sourceAccount': sourceAccount,
    };
  }
}

class FamilyTransactionModel {
  final String id;
  final String memberId;
  final String type;
  final int amountMinor;
  final int balanceAfterMinor;
  final String category;
  final String merchantOrSource;
  final String? note;
  final String status;
  final String? declineReason;
  final DateTime timestamp;

  const FamilyTransactionModel({
    required this.id,
    required this.memberId,
    required this.type,
    required this.amountMinor,
    required this.balanceAfterMinor,
    required this.category,
    required this.merchantOrSource,
    this.note,
    required this.status,
    this.declineReason,
    required this.timestamp,
  });

  factory FamilyTransactionModel.fromJson(Map<String, dynamic> json) {
    return FamilyTransactionModel(
      id: json['id'] as String? ?? '',
      memberId: json['memberId'] as String? ?? '',
      type: json['type'] as String? ?? 'SPEND',
      amountMinor: (json['amountMinor'] as num?)?.toInt() ?? 0,
      balanceAfterMinor: (json['balanceAfterMinor'] as num?)?.toInt() ?? 0,
      category: json['category'] as String? ?? 'GENERAL',
      merchantOrSource: json['merchantOrSource'] as String? ?? '',
      note: json['note'] as String?,
      status: json['status'] as String? ?? 'APPROVED',
      declineReason: json['declineReason'] as String?,
      timestamp: json['timestamp'] != null
          ? DateTime.tryParse(json['timestamp'].toString()) ?? DateTime.now()
          : DateTime.now(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'memberId': memberId,
      'type': type,
      'amountMinor': amountMinor,
      'balanceAfterMinor': balanceAfterMinor,
      'category': category,
      'merchantOrSource': merchantOrSource,
      'note': note,
      'status': status,
      'declineReason': declineReason,
      'timestamp': timestamp.toIso8601String(),
    };
  }
}

class FamilyMemberModel {
  final String id;
  final int? guardianUserId;
  final String firstName;
  final String ageBand;
  final String avatarInitials;
  final int balanceMinor;
  final int dailyLimitMinor;
  final int monthlyLimitMinor;
  final List<String> allowedCategories;
  final bool isFrozen;
  final AllowanceScheduleModel? allowanceSchedule;
  final int todaySpentMinor;
  final int monthSpentMinor;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  const FamilyMemberModel({
    required this.id,
    this.guardianUserId,
    required this.firstName,
    required this.ageBand,
    required this.avatarInitials,
    required this.balanceMinor,
    required this.dailyLimitMinor,
    required this.monthlyLimitMinor,
    required this.allowedCategories,
    required this.isFrozen,
    this.allowanceSchedule,
    this.todaySpentMinor = 0,
    this.monthSpentMinor = 0,
    this.createdAt,
    this.updatedAt,
  });

  factory FamilyMemberModel.fromJson(Map<String, dynamic> json) {
    return FamilyMemberModel(
      id: json['id'] as String? ?? '',
      guardianUserId: (json['guardianUserId'] as num?)?.toInt(),
      firstName: json['firstName'] as String? ?? '',
      ageBand: json['ageBand'] as String? ?? 'UNDER_10',
      avatarInitials: json['avatarInitials'] as String? ?? '',
      balanceMinor: (json['balanceMinor'] as num?)?.toInt() ?? 0,
      dailyLimitMinor: (json['dailyLimitMinor'] as num?)?.toInt() ?? 0,
      monthlyLimitMinor: (json['monthlyLimitMinor'] as num?)?.toInt() ?? 0,
      allowedCategories: (json['allowedCategories'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          const [],
      isFrozen: json['isFrozen'] as bool? ?? false,
      allowanceSchedule: json['allowanceSchedule'] != null
          ? AllowanceScheduleModel.fromJson(
              json['allowanceSchedule'] as Map<String, dynamic>)
          : null,
      todaySpentMinor: (json['todaySpentMinor'] as num?)?.toInt() ?? 0,
      monthSpentMinor: (json['monthSpentMinor'] as num?)?.toInt() ?? 0,
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString())
          : null,
      updatedAt: json['updatedAt'] != null
          ? DateTime.tryParse(json['updatedAt'].toString())
          : null,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'guardianUserId': guardianUserId,
      'firstName': firstName,
      'ageBand': ageBand,
      'avatarInitials': avatarInitials,
      'balanceMinor': balanceMinor,
      'dailyLimitMinor': dailyLimitMinor,
      'monthlyLimitMinor': monthlyLimitMinor,
      'allowedCategories': allowedCategories,
      'isFrozen': isFrozen,
      'allowanceSchedule': allowanceSchedule?.toJson(),
      'todaySpentMinor': todaySpentMinor,
      'monthSpentMinor': monthSpentMinor,
      'createdAt': createdAt?.toIso8601String(),
      'updatedAt': updatedAt?.toIso8601String(),
    };
  }

  FamilyMemberModel copyWith({
    String? id,
    int? guardianUserId,
    String? firstName,
    String? ageBand,
    String? avatarInitials,
    int? balanceMinor,
    int? dailyLimitMinor,
    int? monthlyLimitMinor,
    List<String>? allowedCategories,
    bool? isFrozen,
    AllowanceScheduleModel? allowanceSchedule,
    int? todaySpentMinor,
    int? monthSpentMinor,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    return FamilyMemberModel(
      id: id ?? this.id,
      guardianUserId: guardianUserId ?? this.guardianUserId,
      firstName: firstName ?? this.firstName,
      ageBand: ageBand ?? this.ageBand,
      avatarInitials: avatarInitials ?? this.avatarInitials,
      balanceMinor: balanceMinor ?? this.balanceMinor,
      dailyLimitMinor: dailyLimitMinor ?? this.dailyLimitMinor,
      monthlyLimitMinor: monthlyLimitMinor ?? this.monthlyLimitMinor,
      allowedCategories: allowedCategories ?? this.allowedCategories,
      isFrozen: isFrozen ?? this.isFrozen,
      allowanceSchedule: allowanceSchedule ?? this.allowanceSchedule,
      todaySpentMinor: todaySpentMinor ?? this.todaySpentMinor,
      monthSpentMinor: monthSpentMinor ?? this.monthSpentMinor,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }
}

class ChildViewModel {
  final String id;
  final String firstName;
  final String avatarInitials;
  final int balanceMinor;
  final int dailyLimitMinor;
  final int remainingDailyMinor;
  final List<String> allowedCategories;
  final bool isFrozen;
  final List<FamilyTransactionModel> recentTransactions;

  const ChildViewModel({
    required this.id,
    required this.firstName,
    required this.avatarInitials,
    required this.balanceMinor,
    required this.dailyLimitMinor,
    required this.remainingDailyMinor,
    required this.allowedCategories,
    required this.isFrozen,
    required this.recentTransactions,
  });

  factory ChildViewModel.fromJson(Map<String, dynamic> json) {
    return ChildViewModel(
      id: json['id'] as String? ?? '',
      firstName: json['firstName'] as String? ?? '',
      avatarInitials: json['avatarInitials'] as String? ?? '',
      balanceMinor: (json['balanceMinor'] as num?)?.toInt() ?? 0,
      dailyLimitMinor: (json['dailyLimitMinor'] as num?)?.toInt() ?? 0,
      remainingDailyMinor: (json['remainingDailyMinor'] as num?)?.toInt() ?? 0,
      allowedCategories: (json['allowedCategories'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          const [],
      isFrozen: json['isFrozen'] as bool? ?? false,
      recentTransactions: (json['recentTransactions'] as List<dynamic>?)
              ?.map((e) =>
                  FamilyTransactionModel.fromJson(e as Map<String, dynamic>))
              .toList() ??
          const [],
    );
  }
}
