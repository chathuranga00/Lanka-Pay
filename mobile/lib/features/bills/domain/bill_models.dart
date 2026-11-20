import 'package:flutter/material.dart';

enum BillerCategory {
  electricity,
  water,
  mobile,
  internet,
  government,
  utilities;

  static BillerCategory fromString(String? val) {
    if (val == null) return BillerCategory.utilities;
    switch (val.toUpperCase()) {
      case 'ELECTRICITY':
        return BillerCategory.electricity;
      case 'WATER':
        return BillerCategory.water;
      case 'MOBILE':
        return BillerCategory.mobile;
      case 'INTERNET':
        return BillerCategory.internet;
      case 'GOVERNMENT':
        return BillerCategory.government;
      case 'UTILITIES':
      default:
        return BillerCategory.utilities;
    }
  }

  String toBackendString() {
    return name.toUpperCase();
  }

  String get displayName {
    switch (this) {
      case BillerCategory.electricity:
        return 'Electricity';
      case BillerCategory.water:
        return 'Water';
      case BillerCategory.mobile:
        return 'Mobile';
      case BillerCategory.internet:
        return 'Broadband';
      case BillerCategory.government:
        return 'Government';
      case BillerCategory.utilities:
        return 'Utilities';
    }
  }

  IconData get icon {
    switch (this) {
      case BillerCategory.electricity:
        return Icons.bolt_rounded;
      case BillerCategory.water:
        return Icons.water_drop_rounded;
      case BillerCategory.mobile:
        return Icons.phone_android_rounded;
      case BillerCategory.internet:
        return Icons.wifi_rounded;
      case BillerCategory.government:
        return Icons.account_balance_rounded;
      case BillerCategory.utilities:
        return Icons.receipt_long_rounded;
    }
  }

  Color get color {
    switch (this) {
      case BillerCategory.electricity:
        return const Color(0xFFF59E0B);
      case BillerCategory.water:
        return const Color(0xFF0284C7);
      case BillerCategory.mobile:
        return const Color(0xFF10B981);
      case BillerCategory.internet:
        return const Color(0xFF6366F1);
      case BillerCategory.government:
        return const Color(0xFF8B5CF6);
      case BillerCategory.utilities:
        return const Color(0xFFEC4899);
    }
  }
}

class BillerModel {
  final String id;
  final String name;
  final BillerCategory category;
  final String referenceLabel;
  final double serviceFee;
  final bool partialPayAllowed;
  final bool requiresLookup;
  final String? iconName;

  const BillerModel({
    required this.id,
    required this.name,
    required this.category,
    required this.referenceLabel,
    required this.serviceFee,
    required this.partialPayAllowed,
    required this.requiresLookup,
    this.iconName,
  });

  factory BillerModel.fromJson(Map<String, dynamic> json) {
    return BillerModel(
      id: json['id'] as String? ?? '',
      name: json['name'] as String? ?? '',
      category: BillerCategory.fromString(json['category'] as String?),
      referenceLabel: json['referenceLabel'] as String? ?? 'Account Number',
      serviceFee: (json['serviceFee'] as num?)?.toDouble() ?? 0.0,
      partialPayAllowed: json['partialPayAllowed'] as bool? ?? false,
      requiresLookup: json['requiresLookup'] as bool? ?? true,
      iconName: json['iconName'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'category': category.toBackendString(),
    'referenceLabel': referenceLabel,
    'serviceFee': serviceFee,
    'partialPayAllowed': partialPayAllowed,
    'requiresLookup': requiresLookup,
    'iconName': iconName,
  };
}

class BillLookupModel {
  final String billerId;
  final String billerName;
  final BillerCategory category;
  final String customerReference;
  final String maskedReference;
  final String customerName;
  final double amountDue;
  final double minimumAmount;
  final String? dueDate;
  final String billStatus;
  final String statusMessage;
  final double serviceFee;
  final double totalAmount;

  const BillLookupModel({
    required this.billerId,
    required this.billerName,
    required this.category,
    required this.customerReference,
    required this.maskedReference,
    required this.customerName,
    required this.amountDue,
    required this.minimumAmount,
    this.dueDate,
    required this.billStatus,
    required this.statusMessage,
    required this.serviceFee,
    required this.totalAmount,
  });

  factory BillLookupModel.fromJson(Map<String, dynamic> json) {
    return BillLookupModel(
      billerId: json['billerId'] as String? ?? '',
      billerName: json['billerName'] as String? ?? '',
      category: BillerCategory.fromString(json['category'] as String?),
      customerReference: json['customerReference'] as String? ?? '',
      maskedReference: json['maskedReference'] as String? ?? '',
      customerName: json['customerName'] as String? ?? '',
      amountDue: (json['amountDue'] as num?)?.toDouble() ?? 0.0,
      minimumAmount: (json['minimumAmount'] as num?)?.toDouble() ?? 0.0,
      dueDate: json['dueDate'] as String?,
      billStatus: json['billStatus'] as String? ?? 'BILL_FOUND',
      statusMessage: json['statusMessage'] as String? ?? '',
      serviceFee: (json['serviceFee'] as num?)?.toDouble() ?? 0.0,
      totalAmount: (json['totalAmount'] as num?)?.toDouble() ?? 0.0,
    );
  }

  bool get isBillFound => billStatus == 'BILL_FOUND';
  bool get isNoBillDue => billStatus == 'NO_BILL_DUE';
  bool get isAmountChanged => billStatus == 'AMOUNT_CHANGED';
  bool get isInvalidReference => billStatus == 'INVALID_REFERENCE';
}

class SavedBillerModel {
  final int id;
  final String billerId;
  final String billerName;
  final BillerCategory category;
  final String nickname;
  final String maskedReference;
  final String? createdAt;

  const SavedBillerModel({
    required this.id,
    required this.billerId,
    required this.billerName,
    required this.category,
    required this.nickname,
    required this.maskedReference,
    this.createdAt,
  });

  factory SavedBillerModel.fromJson(Map<String, dynamic> json) {
    return SavedBillerModel(
      id: (json['id'] as num?)?.toInt() ?? 0,
      billerId: json['billerId'] as String? ?? '',
      billerName: json['billerName'] as String? ?? '',
      category: BillerCategory.fromString(json['category'] as String?),
      nickname: json['nickname'] as String? ?? '',
      maskedReference: json['maskedReference'] as String? ?? '',
      createdAt: json['createdAt'] as String?,
    );
  }

  SavedBillerModel copyWith({String? nickname}) {
    return SavedBillerModel(
      id: id,
      billerId: billerId,
      billerName: billerName,
      category: category,
      nickname: nickname ?? this.nickname,
      maskedReference: maskedReference,
      createdAt: createdAt,
    );
  }
}

class BillPaymentReceiptModel {
  final String receiptNumber;
  final int paymentId;
  final String reference;
  final String billerId;
  final String billerName;
  final BillerCategory category;
  final String maskedReference;
  final String customerName;
  final double amount;
  final double fee;
  final double totalAmount;
  final String currency;
  final String status; // COMPLETED, PENDING, REQUIRES_RECONCILIATION, FAILED, REVERSED
  final String? billerConfirmationRef;
  final String? failureReason;
  final String fundingType; // ACCOUNT or CARD
  final String? fundingLabel;
  final String? billDueDate;
  final String? timestamp;

  const BillPaymentReceiptModel({
    required this.receiptNumber,
    required this.paymentId,
    required this.reference,
    required this.billerId,
    required this.billerName,
    required this.category,
    required this.maskedReference,
    required this.customerName,
    required this.amount,
    required this.fee,
    required this.totalAmount,
    required this.currency,
    required this.status,
    this.billerConfirmationRef,
    this.failureReason,
    required this.fundingType,
    this.fundingLabel,
    this.billDueDate,
    this.timestamp,
  });

  factory BillPaymentReceiptModel.fromJson(Map<String, dynamic> json) {
    return BillPaymentReceiptModel(
      receiptNumber: json['receiptNumber'] as String? ?? '',
      paymentId: (json['paymentId'] as num?)?.toInt() ?? 0,
      reference: json['reference'] as String? ?? '',
      billerId: json['billerId'] as String? ?? '',
      billerName: json['billerName'] as String? ?? '',
      category: BillerCategory.fromString(json['category'] as String?),
      maskedReference: json['maskedReference'] as String? ?? '',
      customerName: json['customerName'] as String? ?? '',
      amount: (json['amount'] as num?)?.toDouble() ?? 0.0,
      fee: (json['fee'] as num?)?.toDouble() ?? 0.0,
      totalAmount: (json['totalAmount'] as num?)?.toDouble() ?? 0.0,
      currency: json['currency'] as String? ?? 'LKR',
      status: json['status'] as String? ?? 'COMPLETED',
      billerConfirmationRef: json['billerConfirmationRef'] as String?,
      failureReason: json['failureReason'] as String?,
      fundingType: json['fundingType'] as String? ?? 'ACCOUNT',
      fundingLabel: json['fundingLabel'] as String?,
      billDueDate: json['billDueDate'] as String?,
      timestamp: json['timestamp'] as String?,
    );
  }

  bool get isCompleted => status == 'COMPLETED';
  bool get isPending => status == 'PENDING' || status == 'REQUIRES_RECONCILIATION';
  bool get isFailed => status == 'FAILED';
  bool get isReversed => status == 'REVERSED';
}
