import 'package:flutter/material.dart';

class CategoryTotalModel {
  final String category;
  final String displayName;
  final String colorHex;
  final int totalMinor;
  final double totalAmount;
  final int count;
  final double percentage;

  const CategoryTotalModel({
    required this.category,
    required this.displayName,
    required this.colorHex,
    required this.totalMinor,
    required this.totalAmount,
    required this.count,
    required this.percentage,
  });

  factory CategoryTotalModel.fromJson(Map<String, dynamic> json) {
    final hex = json['colorHex'] as String? ?? '#8B5CF6';
    return CategoryTotalModel(
      category: json['category'] as String? ?? 'OTHER',
      displayName: json['displayName'] as String? ?? 'Other',
      colorHex: hex.startsWith('#') ? hex : '#$hex',
      totalMinor: (json['totalMinor'] as num?)?.toInt() ?? 0,
      totalAmount: (json['totalAmount'] as num?)?.toDouble() ?? 0.0,
      count: (json['count'] as num?)?.toInt() ?? 0,
      percentage: (json['percentage'] as num?)?.toDouble() ?? 0.0,
    );
  }

  Color get color {
    try {
      final clean = colorHex.replaceFirst('#', '');
      return Color(int.parse('FF$clean', radix: 16));
    } catch (_) {
      return const Color(0xFF8B5CF6);
    }
  }
}

class TopPayeeModel {
  final String name;
  final int totalMinor;
  final double totalAmount;
  final int count;

  const TopPayeeModel({
    required this.name,
    required this.totalMinor,
    required this.totalAmount,
    required this.count,
  });

  factory TopPayeeModel.fromJson(Map<String, dynamic> json) {
    return TopPayeeModel(
      name: json['name'] as String? ?? 'Unknown',
      totalMinor: (json['totalMinor'] as num?)?.toInt() ?? 0,
      totalAmount: (json['totalAmount'] as num?)?.toDouble() ?? 0.0,
      count: (json['count'] as num?)?.toInt() ?? 0,
    );
  }
}

class MonthOverMonthModel {
  final int currentMonthSpentMinor;
  final double currentMonthSpent;
  final int previousMonthSpentMinor;
  final double previousMonthSpent;
  final int differenceMinor;
  final double differenceAmount;
  final double percentageChange;
  final boolean increased;

  const MonthOverMonthModel({
    required this.currentMonthSpentMinor,
    required this.currentMonthSpent,
    required this.previousMonthSpentMinor,
    required this.previousMonthSpent,
    required this.differenceMinor,
    required this.differenceAmount,
    required this.percentageChange,
    required this.increased,
  });

  factory MonthOverMonthModel.fromJson(Map<String, dynamic> json) {
    return MonthOverMonthModel(
      currentMonthSpentMinor: (json['currentMonthSpentMinor'] as num?)?.toInt() ?? 0,
      currentMonthSpent: (json['currentMonthSpent'] as num?)?.toDouble() ?? 0.0,
      previousMonthSpentMinor: (json['previousMonthSpentMinor'] as num?)?.toInt() ?? 0,
      previousMonthSpent: (json['previousMonthSpent'] as num?)?.toDouble() ?? 0.0,
      differenceMinor: (json['differenceMinor'] as num?)?.toInt() ?? 0,
      differenceAmount: (json['differenceAmount'] as num?)?.toDouble() ?? 0.0,
      percentageChange: (json['percentageChange'] as num?)?.toDouble() ?? 0.0,
      increased: json['increased'] as bool? ?? false,
    );
  }
}

typedef boolean = bool;

class AnalyticsSummaryModel {
  final String month;
  final int spentMinor;
  final double spentAmount;
  final int receivedMinor;
  final double receivedAmount;
  final int pendingSpentMinor;
  final double pendingSpentAmount;
  final int pendingCount;
  final List<CategoryTotalModel> byCategoryTotals;
  final List<TopPayeeModel> topPayees;
  final MonthOverMonthModel? monthOverMonth;
  final int? budgetTotalMinor;
  final double? budgetTotalAmount;
  final double? budgetUsedPercentage;
  final int? budgetRemainingMinor;
  final double? budgetRemainingAmount;

  const AnalyticsSummaryModel({
    required this.month,
    required this.spentMinor,
    required this.spentAmount,
    required this.receivedMinor,
    required this.receivedAmount,
    required this.pendingSpentMinor,
    required this.pendingSpentAmount,
    required this.pendingCount,
    required this.byCategoryTotals,
    required this.topPayees,
    this.monthOverMonth,
    this.budgetTotalMinor,
    this.budgetTotalAmount,
    this.budgetUsedPercentage,
    this.budgetRemainingMinor,
    this.budgetRemainingAmount,
  });

  factory AnalyticsSummaryModel.fromJson(Map<String, dynamic> json) {
    final catList = (json['byCategoryTotals'] as List<dynamic>?)
            ?.map((e) => CategoryTotalModel.fromJson(e as Map<String, dynamic>))
            .toList() ??
        [];
    final payeeList = (json['topPayees'] as List<dynamic>?)
            ?.map((e) => TopPayeeModel.fromJson(e as Map<String, dynamic>))
            .toList() ??
        [];

    return AnalyticsSummaryModel(
      month: json['month'] as String? ?? '',
      spentMinor: (json['spentMinor'] as num?)?.toInt() ?? 0,
      spentAmount: (json['spentAmount'] as num?)?.toDouble() ?? 0.0,
      receivedMinor: (json['receivedMinor'] as num?)?.toInt() ?? 0,
      receivedAmount: (json['receivedAmount'] as num?)?.toDouble() ?? 0.0,
      pendingSpentMinor: (json['pendingSpentMinor'] as num?)?.toInt() ?? 0,
      pendingSpentAmount: (json['pendingSpentAmount'] as num?)?.toDouble() ?? 0.0,
      pendingCount: (json['pendingCount'] as num?)?.toInt() ?? 0,
      byCategoryTotals: catList,
      topPayees: payeeList,
      monthOverMonth: json['monthOverMonth'] != null
          ? MonthOverMonthModel.fromJson(json['monthOverMonth'] as Map<String, dynamic>)
          : null,
      budgetTotalMinor: (json['budgetTotalMinor'] as num?)?.toInt(),
      budgetTotalAmount: (json['budgetTotalAmount'] as num?)?.toDouble(),
      budgetUsedPercentage: (json['budgetUsedPercentage'] as num?)?.toDouble(),
      budgetRemainingMinor: (json['budgetRemainingMinor'] as num?)?.toInt(),
      budgetRemainingAmount: (json['budgetRemainingAmount'] as num?)?.toDouble(),
    );
  }
}

class BudgetModel {
  final String id;
  final int userId;
  final String month;
  final int monthlyTotalMinor;
  final double monthlyTotalAmount;
  final int spentMinor;
  final double spentAmount;
  final double usedPercentage;
  final int remainingMinor;
  final double remainingAmount;
  final Map<String, int> categoryBudgetsMinor;
  final bool notified80;
  final bool notified100;

  const BudgetModel({
    required this.id,
    required this.userId,
    required this.month,
    required this.monthlyTotalMinor,
    required this.monthlyTotalAmount,
    required this.spentMinor,
    required this.spentAmount,
    required this.usedPercentage,
    required this.remainingMinor,
    required this.remainingAmount,
    required this.categoryBudgetsMinor,
    required this.notified80,
    required this.notified100,
  });

  factory BudgetModel.fromJson(Map<String, dynamic> json) {
    final catBudgets = <String, int>{};
    if (json['categoryBudgetsMinor'] is Map) {
      (json['categoryBudgetsMinor'] as Map).forEach((k, v) {
        catBudgets[k.toString()] = (v as num).toInt();
      });
    }

    return BudgetModel(
      id: json['id'] as String? ?? '',
      userId: (json['userId'] as num?)?.toInt() ?? 0,
      month: json['month'] as String? ?? '',
      monthlyTotalMinor: (json['monthlyTotalMinor'] as num?)?.toInt() ?? 0,
      monthlyTotalAmount: (json['monthlyTotalAmount'] as num?)?.toDouble() ?? 0.0,
      spentMinor: (json['spentMinor'] as num?)?.toInt() ?? 0,
      spentAmount: (json['spentAmount'] as num?)?.toDouble() ?? 0.0,
      usedPercentage: (json['usedPercentage'] as num?)?.toDouble() ?? 0.0,
      remainingMinor: (json['remainingMinor'] as num?)?.toInt() ?? 0,
      remainingAmount: (json['remainingAmount'] as num?)?.toDouble() ?? 0.0,
      categoryBudgetsMinor: catBudgets,
      notified80: json['notified80'] as bool? ?? false,
      notified100: json['notified100'] as bool? ?? false,
    );
  }
}

class ActivityItemModel {
  final int id;
  final String type; // "TRANSFER" or "BILL"
  final String reference;
  final String title;
  final String subtitle;
  final int amountMinor;
  final double amount;
  final String currency;
  final String direction; // "DEBIT" or "CREDIT"
  final String status;
  final String category;
  final String colorHex;
  final String fundingType;
  final String? cardLast4;
  final String? bankCode;
  final DateTime createdAt;

  const ActivityItemModel({
    required this.id,
    required this.type,
    required this.reference,
    required this.title,
    required this.subtitle,
    required this.amountMinor,
    required this.amount,
    required this.currency,
    required this.direction,
    required this.status,
    required this.category,
    required this.colorHex,
    required this.fundingType,
    this.cardLast4,
    this.bankCode,
    required this.createdAt,
  });

  factory ActivityItemModel.fromJson(Map<String, dynamic> json) {
    final hex = json['colorHex'] as String? ?? '#8B5CF6';
    return ActivityItemModel(
      id: (json['id'] as num?)?.toInt() ?? 0,
      type: json['type'] as String? ?? 'TRANSFER',
      reference: json['reference'] as String? ?? '',
      title: json['title'] as String? ?? 'Transaction',
      subtitle: json['subtitle'] as String? ?? '',
      amountMinor: (json['amountMinor'] as num?)?.toInt() ?? 0,
      amount: (json['amount'] as num?)?.toDouble() ?? 0.0,
      currency: json['currency'] as String? ?? 'LKR',
      direction: json['direction'] as String? ?? 'DEBIT',
      status: json['status'] as String? ?? 'COMPLETED',
      category: json['category'] as String? ?? 'OTHER',
      colorHex: hex.startsWith('#') ? hex : '#$hex',
      fundingType: json['fundingType'] as String? ?? 'ACCOUNT',
      cardLast4: json['cardLast4'] as String?,
      bankCode: json['bankCode'] as String?,
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
    );
  }

  Color get color {
    try {
      final clean = colorHex.replaceFirst('#', '');
      return Color(int.parse('FF$clean', radix: 16));
    } catch (_) {
      return const Color(0xFF8B5CF6);
    }
  }
}

class ActivityFilter {
  final String? month;
  final String? direction;
  final String? category;

  const ActivityFilter({
    this.month,
    this.direction,
    this.category,
  });

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ActivityFilter &&
          runtimeType == other.runtimeType &&
          month == other.month &&
          direction == other.direction &&
          category == other.category;

  @override
  int get hashCode => Object.hash(month, direction, category);
}

