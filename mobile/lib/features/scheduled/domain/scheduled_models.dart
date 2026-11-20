import 'package:flutter/material.dart';
import '../../../core/theme/app_tokens.dart';

enum ScheduleType {
  transfer,
  bill;

  static ScheduleType fromString(String? val) {
    if (val == null) return ScheduleType.transfer;
    switch (val.toUpperCase()) {
      case 'BILL':
        return ScheduleType.bill;
      case 'TRANSFER':
      default:
        return ScheduleType.transfer;
    }
  }

  String toBackendString() => name.toUpperCase();

  String get displayName {
    switch (this) {
      case ScheduleType.transfer:
        return 'Transfer';
      case ScheduleType.bill:
        return 'Bill Payment';
    }
  }

  IconData get icon {
    switch (this) {
      case ScheduleType.transfer:
        return Icons.swap_horiz_rounded;
      case ScheduleType.bill:
        return Icons.receipt_long_rounded;
    }
  }
}

enum ScheduleFrequency {
  once,
  weekly,
  monthly;

  static ScheduleFrequency fromString(String? val) {
    if (val == null) return ScheduleFrequency.monthly;
    switch (val.toUpperCase()) {
      case 'ONCE':
        return ScheduleFrequency.once;
      case 'WEEKLY':
        return ScheduleFrequency.weekly;
      case 'MONTHLY':
      default:
        return ScheduleFrequency.monthly;
    }
  }

  String toBackendString() => name.toUpperCase();

  String get displayName {
    switch (this) {
      case ScheduleFrequency.once:
        return 'One-time';
      case ScheduleFrequency.weekly:
        return 'Weekly';
      case ScheduleFrequency.monthly:
        return 'Monthly';
    }
  }
}

enum ScheduleStatus {
  active,
  paused,
  cancelled,
  completed;

  static ScheduleStatus fromString(String? val) {
    if (val == null) return ScheduleStatus.active;
    switch (val.toUpperCase()) {
      case 'PAUSED':
        return ScheduleStatus.paused;
      case 'CANCELLED':
        return ScheduleStatus.cancelled;
      case 'COMPLETED':
        return ScheduleStatus.completed;
      case 'ACTIVE':
      default:
        return ScheduleStatus.active;
    }
  }

  String toBackendString() => name.toUpperCase();

  String get displayName {
    switch (this) {
      case ScheduleStatus.active:
        return 'Active';
      case ScheduleStatus.paused:
        return 'Paused';
      case ScheduleStatus.cancelled:
        return 'Cancelled';
      case ScheduleStatus.completed:
        return 'Completed';
    }
  }

  Color get badgeColor {
    switch (this) {
      case ScheduleStatus.active:
        return AppTokens.success;
      case ScheduleStatus.paused:
        return AppTokens.warning;
      case ScheduleStatus.cancelled:
        return AppTokens.error;
      case ScheduleStatus.completed:
        return AppTokens.textTertiaryLight;
    }
  }

  Color get badgeBgColor {
    switch (this) {
      case ScheduleStatus.active:
        return AppTokens.success.withOpacity(0.12);
      case ScheduleStatus.paused:
        return AppTokens.warning.withOpacity(0.12);
      case ScheduleStatus.cancelled:
        return AppTokens.error.withOpacity(0.12);
      case ScheduleStatus.completed:
        return AppTokens.textTertiaryLight.withOpacity(0.15);
    }
  }
}

class ScheduledPayeeModel {
  final ScheduleType type;
  final String? recipientIdentity;
  final String? recipientName;
  final String? billerId;
  final String? billerName;
  final String? customerReference;
  final int? savedBillerId;

  const ScheduledPayeeModel({
    required this.type,
    this.recipientIdentity,
    this.recipientName,
    this.billerId,
    this.billerName,
    this.customerReference,
    this.savedBillerId,
  });

  factory ScheduledPayeeModel.fromJson(Map<String, dynamic> json) {
    return ScheduledPayeeModel(
      type: ScheduleType.fromString(json['type']?.toString()),
      recipientIdentity: json['recipientIdentity']?.toString(),
      recipientName: json['recipientName']?.toString(),
      billerId: json['billerId']?.toString(),
      billerName: json['billerName']?.toString(),
      customerReference: json['customerReference']?.toString(),
      savedBillerId: json['savedBillerId'] is num ? (json['savedBillerId'] as num).toInt() : null,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'type': type.toBackendString(),
      if (recipientIdentity != null) 'recipientIdentity': recipientIdentity,
      if (recipientName != null) 'recipientName': recipientName,
      if (billerId != null) 'billerId': billerId,
      if (billerName != null) 'billerName': billerName,
      if (customerReference != null) 'customerReference': customerReference,
      if (savedBillerId != null) 'savedBillerId': savedBillerId,
    };
  }

  String get displayTitle {
    if (type == ScheduleType.transfer) {
      if (recipientName != null && recipientName!.isNotEmpty) {
        return recipientName!;
      }
      return recipientIdentity ?? 'Recipient';
    } else {
      if (billerName != null && billerName!.isNotEmpty) {
        return billerName!;
      }
      return billerId ?? 'Biller';
    }
  }

  String get displaySubtitle {
    if (type == ScheduleType.transfer) {
      return recipientIdentity ?? '';
    } else {
      return customerReference ?? '';
    }
  }
}

class ScheduledPaymentModel {
  final String id;
  final int userId;
  final ScheduleType type;
  final ScheduledPayeeModel payee;
  final int? amountMinor;
  final double? amount;
  final String currency;
  final bool isBillAmount;
  final String? fundingSource;
  final ScheduleFrequency frequency;
  final String startDate;
  final String? endDate;
  final DateTime? nextRunAt;
  final ScheduleStatus status;
  final int consecutiveFailures;
  final String? lastFailureReason;
  final int version;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  const ScheduledPaymentModel({
    required this.id,
    required this.userId,
    required this.type,
    required this.payee,
    this.amountMinor,
    this.amount,
    this.currency = 'LKR',
    this.isBillAmount = false,
    this.fundingSource,
    required this.frequency,
    required this.startDate,
    this.endDate,
    this.nextRunAt,
    required this.status,
    this.consecutiveFailures = 0,
    this.lastFailureReason,
    this.version = 0,
    this.createdAt,
    this.updatedAt,
  });

  factory ScheduledPaymentModel.fromJson(Map<String, dynamic> json) {
    DateTime? parseDate(dynamic val) {
      if (val == null) return null;
      try {
        return DateTime.parse(val.toString());
      } catch (_) {
        return null;
      }
    }

    final double? amt = json['amount'] is num
        ? (json['amount'] as num).toDouble()
        : (json['amountMinor'] is num
            ? (json['amountMinor'] as num).toDouble() / 100.0
            : null);

    return ScheduledPaymentModel(
      id: json['id']?.toString() ?? '',
      userId: json['userId'] is num ? (json['userId'] as num).toInt() : 0,
      type: ScheduleType.fromString(json['type']?.toString()),
      payee: ScheduledPayeeModel.fromJson(
        Map<String, dynamic>.from(json['payee'] as Map? ?? {}),
      ),
      amountMinor: json['amountMinor'] is num ? (json['amountMinor'] as num).toInt() : null,
      amount: amt,
      currency: json['currency']?.toString() ?? 'LKR',
      isBillAmount: json['isBillAmount'] == true || json['billAmount'] == true,
      fundingSource: json['fundingSource']?.toString(),
      frequency: ScheduleFrequency.fromString(json['frequency']?.toString()),
      startDate: json['startDate']?.toString() ?? '',
      endDate: json['endDate']?.toString(),
      nextRunAt: parseDate(json['nextRunAt']),
      status: ScheduleStatus.fromString(json['status']?.toString()),
      consecutiveFailures: json['consecutiveFailures'] is num
          ? (json['consecutiveFailures'] as num).toInt()
          : 0,
      lastFailureReason: json['lastFailureReason']?.toString(),
      version: json['version'] is num ? (json['version'] as num).toInt() : 0,
      createdAt: parseDate(json['createdAt']),
      updatedAt: parseDate(json['updatedAt']),
    );
  }

  String get formattedAmount {
    if (isBillAmount || amount == null) {
      return 'Bill Amount';
    }
    return '$currency ${amount!.toStringAsFixed(2)}';
  }

  String get formattedNextRun {
    if (nextRunAt == null) return 'Completed';
    final d = nextRunAt!;
    final year = d.year;
    final month = d.month.toString().padLeft(2, '0');
    final day = d.day.toString().padLeft(2, '0');
    return '$year-$month-$day';
  }

  bool get isActive => status == ScheduleStatus.active;
  bool get isPaused => status == ScheduleStatus.paused;
  bool get isCancelled => status == ScheduleStatus.cancelled;
  bool get isCompleted => status == ScheduleStatus.completed;

  bool get canPause => isActive;
  bool get canResume => isPaused;
  bool get canCancel => !isCancelled && !isCompleted;
}

class ScheduledRunModel {
  final String id;
  final String scheduleId;
  final String runDate;
  final DateTime executedAt;
  final String status; // SUCCESS or FAILED
  final int? amountMinor;
  final double? amount;
  final int? transactionId;
  final String? failureReason;

  const ScheduledRunModel({
    required this.id,
    required this.scheduleId,
    required this.runDate,
    required this.executedAt,
    required this.status,
    this.amountMinor,
    this.amount,
    this.transactionId,
    this.failureReason,
  });

  factory ScheduledRunModel.fromJson(Map<String, dynamic> json) {
    DateTime parseDate(dynamic val) {
      if (val == null) return DateTime.now();
      try {
        return DateTime.parse(val.toString());
      } catch (_) {
        return DateTime.now();
      }
    }

    final double? amt = json['amount'] is num
        ? (json['amount'] as num).toDouble()
        : (json['amountMinor'] is num
            ? (json['amountMinor'] as num).toDouble() / 100.0
            : null);

    return ScheduledRunModel(
      id: json['id']?.toString() ?? '',
      scheduleId: json['scheduleId']?.toString() ?? '',
      runDate: json['runDate']?.toString() ?? '',
      executedAt: parseDate(json['executedAt']),
      status: json['status']?.toString() ?? 'SUCCESS',
      amountMinor: json['amountMinor'] is num ? (json['amountMinor'] as num).toInt() : null,
      amount: amt,
      transactionId: json['transactionId'] is num ? (json['transactionId'] as num).toInt() : null,
      failureReason: json['failureReason']?.toString(),
    );
  }

  bool get isSuccess => status.toUpperCase() == 'SUCCESS';

  String get formattedAmount {
    if (amount == null) return '';
    return 'LKR ${amount!.toStringAsFixed(2)}';
  }
}
