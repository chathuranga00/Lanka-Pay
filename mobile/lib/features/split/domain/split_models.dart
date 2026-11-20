import 'package:flutter/foundation.dart';

enum SplitStatus {
  active,
  completed,
  cancelled,
  expired;

  static SplitStatus fromString(String val) {
    switch (val.toUpperCase()) {
      case 'COMPLETED':
        return SplitStatus.completed;
      case 'CANCELLED':
        return SplitStatus.cancelled;
      case 'EXPIRED':
        return SplitStatus.expired;
      case 'ACTIVE':
      default:
        return SplitStatus.active;
    }
  }

  String get label {
    switch (this) {
      case SplitStatus.completed:
        return 'Completed';
      case SplitStatus.cancelled:
        return 'Cancelled';
      case SplitStatus.expired:
        return 'Expired';
      case SplitStatus.active:
        return 'Active';
    }
  }
}

enum ParticipantStatus {
  invited,
  paid,
  declined,
  expired,
  cancelled;

  static ParticipantStatus fromString(String val) {
    switch (val.toUpperCase()) {
      case 'PAID':
        return ParticipantStatus.paid;
      case 'DECLINED':
        return ParticipantStatus.declined;
      case 'EXPIRED':
        return ParticipantStatus.expired;
      case 'CANCELLED':
        return ParticipantStatus.cancelled;
      case 'INVITED':
      default:
        return ParticipantStatus.invited;
    }
  }

  String get label {
    switch (this) {
      case ParticipantStatus.paid:
        return 'Paid';
      case ParticipantStatus.declined:
        return 'Declined';
      case ParticipantStatus.expired:
        return 'Expired';
      case ParticipantStatus.cancelled:
        return 'Cancelled';
      case ParticipantStatus.invited:
        return 'Invited';
    }
  }
}

@immutable
class SplitParticipantModel {
  final int? userId;
  final String? phone;
  final String? displayName;
  final int shareMinor;
  final ParticipantStatus status;
  final int? paymentRequestId;
  final String? paymentRequestReference;
  final String? qrReference;
  final String? qrPayload;
  final int? transferId;
  final DateTime? paidAt;
  final DateTime? lastRemindedAt;
  final bool canRemind;

  const SplitParticipantModel({
    this.userId,
    this.phone,
    this.displayName,
    required this.shareMinor,
    required this.status,
    this.paymentRequestId,
    this.paymentRequestReference,
    this.qrReference,
    this.qrPayload,
    this.transferId,
    this.paidAt,
    this.lastRemindedAt,
    this.canRemind = true,
  });

  factory SplitParticipantModel.fromJson(Map<String, dynamic> json) {
    return SplitParticipantModel(
      userId: json['userId'] as int?,
      phone: json['phone'] as String?,
      displayName: json['displayName'] as String?,
      shareMinor: (json['shareMinor'] as num?)?.toInt() ?? 0,
      status: ParticipantStatus.fromString(json['status'] as String? ?? 'INVITED'),
      paymentRequestId: json['paymentRequestId'] as int?,
      paymentRequestReference: json['paymentRequestReference'] as String?,
      qrReference: json['qrReference'] as String?,
      qrPayload: json['qrPayload'] as String?,
      transferId: json['transferId'] as int?,
      paidAt: json['paidAt'] != null ? DateTime.tryParse(json['paidAt'] as String) : null,
      lastRemindedAt: json['lastRemindedAt'] != null
          ? DateTime.tryParse(json['lastRemindedAt'] as String)
          : null,
      canRemind: json['canRemind'] as bool? ?? true,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'userId': userId,
      'phone': phone,
      'displayName': displayName,
      'shareMinor': shareMinor,
      'status': status.name.toUpperCase(),
      'paymentRequestId': paymentRequestId,
      'paymentRequestReference': paymentRequestReference,
      'qrReference': qrReference,
      'qrPayload': qrPayload,
      'transferId': transferId,
      'paidAt': paidAt?.toIso8601String(),
      'lastRemindedAt': lastRemindedAt?.toIso8601String(),
      'canRemind': canRemind,
    };
  }

  double get shareAmount => shareMinor / 100.0;
}

@immutable
class SplitModel {
  final String id;
  final int ownerUserId;
  final String ownerName;
  final String title;
  final int totalMinor;
  final int ownerShareMinor;
  final String currency;
  final List<SplitParticipantModel> participants;
  final DateTime expiresAt;
  final SplitStatus status;
  final int totalParticipants;
  final int paidCount;
  final int collectedMinor;
  final double progressPercent;
  final DateTime createdAt;

  const SplitModel({
    required this.id,
    required this.ownerUserId,
    required this.ownerName,
    required this.title,
    required this.totalMinor,
    required this.ownerShareMinor,
    required this.currency,
    required this.participants,
    required this.expiresAt,
    required this.status,
    required this.totalParticipants,
    required this.paidCount,
    required this.collectedMinor,
    required this.progressPercent,
    required this.createdAt,
  });

  factory SplitModel.fromJson(Map<String, dynamic> json) {
    final rawParticipants = json['participants'] as List<dynamic>? ?? [];
    return SplitModel(
      id: json['id'] as String? ?? '',
      ownerUserId: (json['ownerUserId'] as num?)?.toInt() ?? 0,
      ownerName: json['ownerName'] as String? ?? 'Split Owner',
      title: json['title'] as String? ?? 'Bill Split',
      totalMinor: (json['totalMinor'] as num?)?.toInt() ?? 0,
      ownerShareMinor: (json['ownerShareMinor'] as num?)?.toInt() ?? 0,
      currency: json['currency'] as String? ?? 'LKR',
      participants: rawParticipants
          .map((p) => SplitParticipantModel.fromJson(p as Map<String, dynamic>))
          .toList(),
      expiresAt: json['expiresAt'] != null
          ? DateTime.tryParse(json['expiresAt'] as String) ?? DateTime.now()
          : DateTime.now(),
      status: SplitStatus.fromString(json['status'] as String? ?? 'ACTIVE'),
      totalParticipants: (json['totalParticipants'] as num?)?.toInt() ?? 0,
      paidCount: (json['paidCount'] as num?)?.toInt() ?? 0,
      collectedMinor: (json['collectedMinor'] as num?)?.toInt() ?? 0,
      progressPercent: (json['progressPercent'] as num?)?.toDouble() ?? 0.0,
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'] as String) ?? DateTime.now()
          : DateTime.now(),
    );
  }

  double get totalAmount => totalMinor / 100.0;
  double get ownerShareAmount => ownerShareMinor / 100.0;
  double get collectedAmount => collectedMinor / 100.0;
  bool get isExpired => DateTime.now().isAfter(expiresAt);
}
