import 'package:flutter/foundation.dart';

/// Domain model for simulated savings goals in the mobile app.
@immutable
class GoalModel {
  final String id;
  final String name;
  final int targetMinor;
  final int savedMinor;
  final DateTime? deadline;
  final String sourceAccount;
  final String status; // 'ACTIVE', 'COMPLETED', 'CANCELLED'
  final String ledgerType; // 'SIMULATED'
  final double progressPercentage;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  const GoalModel({
    required this.id,
    required this.name,
    required this.targetMinor,
    required this.savedMinor,
    this.deadline,
    required this.sourceAccount,
    this.status = 'ACTIVE',
    this.ledgerType = 'SIMULATED',
    this.progressPercentage = 0.0,
    this.createdAt,
    this.updatedAt,
  });

  double get progressRatio =>
      targetMinor > 0 ? (savedMinor / targetMinor).clamp(0.0, 1.0) : 0.0;

  String get formattedSaved =>
      'LKR ${(savedMinor / 100).toStringAsFixed(2)}';

  String get formattedTarget =>
      'LKR ${(targetMinor / 100).toStringAsFixed(2)}';

  bool get isCompleted => status == 'COMPLETED' || savedMinor >= targetMinor;

  GoalModel copyWith({
    String? id,
    String? name,
    int? targetMinor,
    int? savedMinor,
    DateTime? deadline,
    String? sourceAccount,
    String? status,
    String? ledgerType,
    double? progressPercentage,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    return GoalModel(
      id: id ?? this.id,
      name: name ?? this.name,
      targetMinor: targetMinor ?? this.targetMinor,
      savedMinor: savedMinor ?? this.savedMinor,
      deadline: deadline ?? this.deadline,
      sourceAccount: sourceAccount ?? this.sourceAccount,
      status: status ?? this.status,
      ledgerType: ledgerType ?? this.ledgerType,
      progressPercentage: progressPercentage ?? this.progressPercentage,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  factory GoalModel.fromJson(Map<String, dynamic> json) {
    final target = json['targetMinor'] as int? ?? 0;
    final saved = json['savedMinor'] as int? ?? 0;
    final progress = (json['progressPercentage'] as num?)?.toDouble() ??
        (target > 0 ? (saved / target) * 100.0 : 0.0);

    return GoalModel(
      id: json['id'] as String? ?? '',
      name: json['name'] as String? ?? '',
      targetMinor: target,
      savedMinor: saved,
      deadline: json['deadline'] != null
          ? DateTime.tryParse(json['deadline'].toString())
          : null,
      sourceAccount: json['sourceAccount'] as String? ?? 'ACC-1001',
      status: json['status'] as String? ?? 'ACTIVE',
      ledgerType: json['ledgerType'] as String? ?? 'SIMULATED',
      progressPercentage: progress,
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
      'name': name,
      'targetMinor': targetMinor,
      'savedMinor': savedMinor,
      'deadline': deadline?.toIso8601String(),
      'sourceAccount': sourceAccount,
      'status': status,
      'ledgerType': ledgerType,
      'progressPercentage': progressPercentage,
      'createdAt': createdAt?.toIso8601String(),
      'updatedAt': updatedAt?.toIso8601String(),
    };
  }
}

/// Domain model for an append-only simulated ledger entry.
@immutable
class GoalLedgerEntryModel {
  final String id;
  final String goalId;
  final String type; // 'CONTRIBUTION', 'WITHDRAWAL'
  final int amountMinor;
  final int balanceAfterMinor;
  final String? idempotencyKey;
  final String? note;
  final DateTime timestamp;
  final String ledgerType;

  const GoalLedgerEntryModel({
    required this.id,
    required this.goalId,
    required this.type,
    required this.amountMinor,
    required this.balanceAfterMinor,
    this.idempotencyKey,
    this.note,
    required this.timestamp,
    this.ledgerType = 'SIMULATED',
  });

  String get formattedAmount =>
      '${type == 'WITHDRAWAL' ? '-' : '+'}LKR ${(amountMinor / 100).toStringAsFixed(2)}';

  String get formattedBalanceAfter =>
      'LKR ${(balanceAfterMinor / 100).toStringAsFixed(2)}';

  factory GoalLedgerEntryModel.fromJson(Map<String, dynamic> json) {
    return GoalLedgerEntryModel(
      id: json['id'] as String? ?? '',
      goalId: json['goalId'] as String? ?? '',
      type: json['type'] as String? ?? 'CONTRIBUTION',
      amountMinor: json['amountMinor'] as int? ?? 0,
      balanceAfterMinor: json['balanceAfterMinor'] as int? ?? 0,
      idempotencyKey: json['idempotencyKey'] as String?,
      note: json['note'] as String?,
      timestamp: json['timestamp'] != null
          ? DateTime.tryParse(json['timestamp'].toString()) ?? DateTime.now()
          : DateTime.now(),
      ledgerType: json['ledgerType'] as String? ?? 'SIMULATED',
    );
  }
}
