class LookupResultModel {
  final String resultType;
  final String? displayName;
  final String? verificationStatus;
  final List<EligibleAccountModel>? accounts;

  const LookupResultModel({
    required this.resultType,
    this.displayName,
    this.verificationStatus,
    this.accounts,
  });

  bool get isFound => resultType == 'FOUND';

  factory LookupResultModel.fromJson(Map<String, dynamic> json) {
    var rawAccounts = json['accounts'];
    List<EligibleAccountModel>? accountsList;
    if (rawAccounts is List) {
      accountsList = rawAccounts
          .map((a) => EligibleAccountModel.fromJson(Map<String, dynamic>.from(a as Map)))
          .toList();
    }

    return LookupResultModel(
      resultType: json['resultType'] as String? ?? 'NOT_FOUND',
      displayName: json['displayName'] as String?,
      verificationStatus: json['verificationStatus'] as String?,
      accounts: accountsList,
    );
  }
}

class EligibleAccountModel {
  final String institutionCode;
  final String bankDisplayName;
  final String maskedNumber;
  final bool isDefault;

  const EligibleAccountModel({
    required this.institutionCode,
    required this.bankDisplayName,
    required this.maskedNumber,
    required this.isDefault,
  });

  factory EligibleAccountModel.fromJson(Map<String, dynamic> json) {
    return EligibleAccountModel(
      institutionCode: json['institutionCode'] as String? ?? '',
      bankDisplayName: json['bankDisplayName'] as String? ?? '',
      maskedNumber: json['maskedNumber'] as String? ?? '',
      isDefault: json['isDefault'] as bool? ?? false,
    );
  }
}

enum FundingType {
  account,
  card;

  static FundingType fromString(String? type) {
    if (type?.toUpperCase() == 'CARD') return FundingType.card;
    return FundingType.account;
  }

  String toBackendString() => this == FundingType.card ? 'CARD' : 'ACCOUNT';
}

class FundingSourceModel {
  final FundingType type;
  final int id;

  const FundingSourceModel({
    required this.type,
    required this.id,
  });

  Map<String, dynamic> toJson() => {
    'type': type.toBackendString(),
    'id': id,
  };

  factory FundingSourceModel.fromJson(Map<String, dynamic> json) {
    return FundingSourceModel(
      type: FundingType.fromString(json['type'] as String?),
      id: json['id'] is int ? json['id'] as int : int.tryParse(json['id']?.toString() ?? '0') ?? 0,
    );
  }
}

class QuoteModel {
  final String quoteReference;
  final int? payeeId;
  final String? payeeDisplayName;
  final String? maskedPhone;
  final double amount;
  final double fee;
  final double totalAmount;
  final String currency;
  final String? expiresAt;
  final bool simulated;
  final String? feeLabel;
  final double? remainingDailyLimit;
  final FundingSourceModel? fundingSource;

  const QuoteModel({
    required this.quoteReference,
    this.payeeId,
    this.payeeDisplayName,
    this.maskedPhone,
    required this.amount,
    required this.fee,
    required this.totalAmount,
    this.currency = 'LKR',
    this.expiresAt,
    this.simulated = true,
    this.feeLabel,
    this.remainingDailyLimit,
    this.fundingSource,
  });

  factory QuoteModel.fromJson(Map<String, dynamic> json) {
    FundingSourceModel? fs;
    if (json['fundingSource'] is Map) {
      fs = FundingSourceModel.fromJson(Map<String, dynamic>.from(json['fundingSource'] as Map));
    }

    return QuoteModel(
      quoteReference: json['quoteReference'] as String? ?? '',
      payeeId: json['payeeId'] as int?,
      payeeDisplayName: json['payeeDisplayName'] as String?,
      maskedPhone: json['maskedPhone'] as String?,
      amount: (json['amount'] as num?)?.toDouble() ?? 0.0,
      fee: (json['fee'] as num?)?.toDouble() ?? 0.0,
      totalAmount: (json['totalAmount'] as num?)?.toDouble() ?? 0.0,
      currency: json['currency'] as String? ?? 'LKR',
      expiresAt: json['expiresAt'] as String?,
      simulated: json['simulated'] as bool? ?? true,
      feeLabel: json['feeLabel'] as String?,
      remainingDailyLimit: (json['remainingDailyLimit'] as num?)?.toDouble(),
      fundingSource: fs,
    );
  }
}

enum TransferStatusType {
  quoted,
  pending,
  challengeRequired,
  processing,
  completed,
  failed,
  requiresReconciliation,
  reversalPending,
  reversed,
  unknown;

  static TransferStatusType fromString(String? status) {
    switch (status?.toUpperCase()) {
      case 'QUOTED':
        return TransferStatusType.quoted;
      case 'PENDING':
        return TransferStatusType.pending;
      case 'CHALLENGE_REQUIRED':
        return TransferStatusType.challengeRequired;
      case 'PROCESSING':
        return TransferStatusType.processing;
      case 'COMPLETED':
        return TransferStatusType.completed;
      case 'FAILED':
        return TransferStatusType.failed;
      case 'REQUIRES_RECONCILIATION':
        return TransferStatusType.requiresReconciliation;
      case 'REVERSAL_PENDING':
        return TransferStatusType.reversalPending;
      case 'REVERSED':
        return TransferStatusType.reversed;
      default:
        return TransferStatusType.unknown;
    }
  }

  bool get isConfirmedSuccess => this == TransferStatusType.completed;
  bool get isChallengeRequired => this == TransferStatusType.challengeRequired;
  bool get isPendingOrReconciling =>
      this == TransferStatusType.pending ||
      this == TransferStatusType.processing ||
      this == TransferStatusType.requiresReconciliation ||
      this == TransferStatusType.reversalPending;
  bool get isFailed => this == TransferStatusType.failed;
  bool get isReversed => this == TransferStatusType.reversed;
}

class TransferModel {
  final int transferId;
  final String reference;
  final TransferStatusType status;
  final double amount;
  final String currency;
  final int? senderId;
  final String? senderPhone;
  final int? receiverId;
  final String? receiverDisplayName;
  final String? receiverPhone;
  final String? bankTxRef;
  final String? providerReference;
  final String? note;
  final String? failureReason;
  final bool simulated;
  final String? createdAt;
  final String? updatedAt;
  final int? senderAccountId;
  final int? receiverAccountId;
  final FundingType? fundingType;
  final int? cardId;
  final String? cardLast4;
  final String? cardKind;
  final double fee;
  final double totalAmount;
  final String? paymentMethod;
  final String? result;

  const TransferModel({
    required this.transferId,
    required this.reference,
    required this.status,
    required this.amount,
    this.currency = 'LKR',
    this.senderId,
    this.senderPhone,
    this.receiverId,
    this.receiverDisplayName,
    this.receiverPhone,
    this.bankTxRef,
    this.providerReference,
    this.note,
    this.failureReason,
    this.simulated = true,
    this.createdAt,
    this.updatedAt,
    this.senderAccountId,
    this.receiverAccountId,
    this.fundingType,
    this.cardId,
    this.cardLast4,
    this.cardKind,
    this.fee = 0.0,
    this.totalAmount = 0.0,
    this.paymentMethod,
    this.result,
  });

  factory TransferModel.fromJson(Map<String, dynamic> json) {
    final amt = (json['amount'] as num?)?.toDouble() ?? 0.0;
    final feeVal = (json['fee'] as num?)?.toDouble() ?? 0.0;
    final totalVal = (json['totalAmount'] as num?)?.toDouble() ?? (amt + feeVal);

    return TransferModel(
      transferId: json['transferId'] is int
          ? json['transferId'] as int
          : int.tryParse(json['transferId']?.toString() ?? '0') ?? 0,
      reference: json['reference'] as String? ?? '',
      status: TransferStatusType.fromString(json['status'] as String?),
      amount: amt,
      currency: json['currency'] as String? ?? 'LKR',
      senderId: json['senderId'] as int?,
      senderPhone: json['senderPhone'] as String?,
      receiverId: json['receiverId'] as int?,
      receiverDisplayName: json['receiverDisplayName'] as String?,
      receiverPhone: json['receiverPhone'] as String?,
      bankTxRef: json['bankTxRef'] as String?,
      providerReference: json['providerReference'] as String?,
      note: json['note'] as String?,
      failureReason: json['failureReason'] as String?,
      simulated: json['simulated'] as bool? ?? true,
      createdAt: json['createdAt'] as String?,
      updatedAt: json['updatedAt'] as String?,
      senderAccountId: json['senderAccountId'] is int
          ? json['senderAccountId'] as int
          : int.tryParse(json['senderAccountId']?.toString() ?? ''),
      receiverAccountId: json['receiverAccountId'] is int
          ? json['receiverAccountId'] as int
          : int.tryParse(json['receiverAccountId']?.toString() ?? ''),
      fundingType: json['fundingType'] != null ? FundingType.fromString(json['fundingType'] as String?) : null,
      cardId: json['cardId'] is int ? json['cardId'] as int : int.tryParse(json['cardId']?.toString() ?? ''),
      cardLast4: json['cardLast4'] as String?,
      cardKind: json['cardKind']?.toString(),
      fee: feeVal,
      totalAmount: totalVal,
      paymentMethod: json['paymentMethod'] as String?,
      result: json['result'] as String?,
    );
  }
}
