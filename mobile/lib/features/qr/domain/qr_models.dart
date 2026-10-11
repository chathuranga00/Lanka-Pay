class QrReferenceModel {
  final String reference;
  final String qrType; // STATIC or DYNAMIC
  final String qrPayload;
  final int? recipientId;
  final String? recipientDisplayName;
  final String? maskedPhone;
  final double? amount;
  final String currency;
  final String? note;
  final String? expiresAt;
  final bool oneTimeUse;
  final String status;
  final String? createdAt;

  const QrReferenceModel({
    required this.reference,
    required this.qrType,
    required this.qrPayload,
    this.recipientId,
    this.recipientDisplayName,
    this.maskedPhone,
    this.amount,
    this.currency = 'LKR',
    this.note,
    this.expiresAt,
    this.oneTimeUse = false,
    this.status = 'ACTIVE',
    this.createdAt,
  });

  bool get isExpired {
    if (expiresAt == null) return false;
    final exp = DateTime.tryParse(expiresAt!);
    if (exp == null) return false;
    return DateTime.now().toUtc().isAfter(exp);
  }

  factory QrReferenceModel.fromJson(Map<String, dynamic> json) {
    return QrReferenceModel(
      reference: json['reference'] as String? ?? '',
      qrType: json['qrType'] as String? ?? 'STATIC',
      qrPayload: json['qrPayload'] as String? ?? '',
      recipientId: json['recipientId'] as int?,
      recipientDisplayName: json['recipientDisplayName'] as String?,
      maskedPhone: json['maskedPhone'] as String?,
      amount: (json['amount'] as num?)?.toDouble(),
      currency: json['currency'] as String? ?? 'LKR',
      note: json['note'] as String?,
      expiresAt: json['expiresAt'] as String?,
      oneTimeUse: json['oneTimeUse'] as bool? ?? false,
      status: json['status'] as String? ?? 'ACTIVE',
      createdAt: json['createdAt'] as String?,
    );
  }
}

class ResolvedQrModel {
  final String reference;
  final String qrType;
  final int? recipientId;
  final String? recipientDisplayName;
  final String? maskedPhone;
  final double? amount;
  final String currency;
  final String? note;
  final String? expiresAt;
  final String status;
  final bool oneTimeUse;
  final String? message;

  const ResolvedQrModel({
    required this.reference,
    required this.qrType,
    this.recipientId,
    this.recipientDisplayName,
    this.maskedPhone,
    this.amount,
    this.currency = 'LKR',
    this.note,
    this.expiresAt,
    this.status = 'ACTIVE',
    this.oneTimeUse = false,
    this.message,
  });

  bool get isExpired {
    if (status == 'EXPIRED') return true;
    if (expiresAt == null) return false;
    final exp = DateTime.tryParse(expiresAt!);
    if (exp == null) return false;
    return DateTime.now().toUtc().isAfter(exp);
  }

  bool get isUsed => status == 'USED';
  bool get isCancelled => status == 'CANCELLED';

  factory ResolvedQrModel.fromJson(Map<String, dynamic> json) {
    return ResolvedQrModel(
      reference: json['reference'] as String? ?? '',
      qrType: json['qrType'] as String? ?? 'STATIC',
      recipientId: json['recipientId'] as int?,
      recipientDisplayName: json['recipientDisplayName'] as String?,
      maskedPhone: json['maskedPhone'] as String?,
      amount: (json['amount'] as num?)?.toDouble(),
      currency: json['currency'] as String? ?? 'LKR',
      note: json['note'] as String?,
      expiresAt: json['expiresAt'] as String?,
      status: json['status'] as String? ?? 'ACTIVE',
      oneTimeUse: json['oneTimeUse'] as bool? ?? false,
      message: json['message'] as String?,
    );
  }
}
