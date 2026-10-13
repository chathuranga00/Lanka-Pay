enum CardKind {
  linked,
  virtual;

  static CardKind fromString(String? val) {
    if (val?.toUpperCase() == 'VIRTUAL') return CardKind.virtual;
    return CardKind.linked;
  }

  String toJson() => name.toUpperCase();
}

enum CardStatus {
  pendingVerification,
  active,
  frozen,
  expired,
  removed;

  static CardStatus fromString(String? val) {
    switch (val?.toUpperCase()) {
      case 'PENDING_VERIFICATION':
        return CardStatus.pendingVerification;
      case 'FROZEN':
        return CardStatus.frozen;
      case 'EXPIRED':
        return CardStatus.expired;
      case 'REMOVED':
        return CardStatus.removed;
      case 'ACTIVE':
      default:
        return CardStatus.active;
    }
  }

  String toJson() {
    switch (this) {
      case CardStatus.pendingVerification:
        return 'PENDING_VERIFICATION';
      case CardStatus.frozen:
        return 'FROZEN';
      case CardStatus.expired:
        return 'EXPIRED';
      case CardStatus.removed:
        return 'REMOVED';
      case CardStatus.active:
        return 'ACTIVE';
    }
  }
}

class CardModel {
  final int id;
  final int? userId;
  final int? linkedBankAccountId;
  final String last4;
  final int expMonth;
  final int expYear;
  final String brandLabel;
  final String? nickname;
  final CardKind cardKind;
  final CardStatus status;
  final bool onlinePaymentsEnabled;
  final int perPaymentLimitMinor;
  final int dailyLimitMinor;
  final DateTime? createdAt;
  final DateTime? updatedAt;
  final DateTime? verifiedAt;
  final bool simulated;

  const CardModel({
    required this.id,
    this.userId,
    this.linkedBankAccountId,
    required this.last4,
    required this.expMonth,
    required this.expYear,
    this.brandLabel = 'TEST CARD',
    this.nickname,
    this.cardKind = CardKind.linked,
    this.status = CardStatus.active,
    this.onlinePaymentsEnabled = true,
    this.perPaymentLimitMinor = 5000000,
    this.dailyLimitMinor = 20000000,
    this.createdAt,
    this.updatedAt,
    this.verifiedAt,
    this.simulated = true,
  });

  bool get isVirtual => cardKind == CardKind.virtual;
  bool get isLinked => cardKind == CardKind.linked;
  bool get isFrozen => status == CardStatus.frozen;
  bool get isActive => status == CardStatus.active;
  bool get isPendingVerification => status == CardStatus.pendingVerification;
  bool get isExpired => status == CardStatus.expired;

  double get dailyLimit => dailyLimitMinor / 100.0;
  double get perPaymentLimit => perPaymentLimitMinor / 100.0;

  String get formattedExpiry {
    final m = expMonth.toString().padLeft(2, '0');
    final yStr = expYear.toString();
    final y = yStr.length >= 2 ? yStr.substring(yStr.length - 2) : yStr.padLeft(2, '0');
    return '$m/$y';
  }

  String get maskedPan => '•••• •••• •••• $last4';

  CardModel copyWith({
    int? id,
    int? userId,
    int? linkedBankAccountId,
    String? last4,
    int? expMonth,
    int? expYear,
    String? brandLabel,
    String? nickname,
    CardKind? cardKind,
    CardStatus? status,
    bool? onlinePaymentsEnabled,
    int? perPaymentLimitMinor,
    int? dailyLimitMinor,
    DateTime? createdAt,
    DateTime? updatedAt,
    DateTime? verifiedAt,
    bool? simulated,
  }) {
    return CardModel(
      id: id ?? this.id,
      userId: userId ?? this.userId,
      linkedBankAccountId: linkedBankAccountId ?? this.linkedBankAccountId,
      last4: last4 ?? this.last4,
      expMonth: expMonth ?? this.expMonth,
      expYear: expYear ?? this.expYear,
      brandLabel: brandLabel ?? this.brandLabel,
      nickname: nickname ?? this.nickname,
      cardKind: cardKind ?? this.cardKind,
      status: status ?? this.status,
      onlinePaymentsEnabled: onlinePaymentsEnabled ?? this.onlinePaymentsEnabled,
      perPaymentLimitMinor: perPaymentLimitMinor ?? this.perPaymentLimitMinor,
      dailyLimitMinor: dailyLimitMinor ?? this.dailyLimitMinor,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      verifiedAt: verifiedAt ?? this.verifiedAt,
      simulated: simulated ?? this.simulated,
    );
  }

  factory CardModel.fromJson(Map<String, dynamic> json) {
    return CardModel(
      id: (json['id'] as num).toInt(),
      userId: (json['userId'] as num?)?.toInt(),
      linkedBankAccountId: (json['linkedBankAccountId'] as num?)?.toInt(),
      last4: json['last4']?.toString() ?? '',
      expMonth: (json['expMonth'] as num?)?.toInt() ?? 12,
      expYear: (json['expYear'] as num?)?.toInt() ?? 27,
      brandLabel: json['brandLabel']?.toString() ?? 'TEST CARD',
      nickname: json['nickname']?.toString(),
      cardKind: CardKind.fromString(json['cardKind']?.toString()),
      status: CardStatus.fromString(json['status']?.toString()),
      onlinePaymentsEnabled: json['onlinePaymentsEnabled'] == true,
      perPaymentLimitMinor: (json['perPaymentLimitMinor'] as num?)?.toInt() ?? 5000000,
      dailyLimitMinor: (json['dailyLimitMinor'] as num?)?.toInt() ?? 20000000,
      createdAt: json['createdAt'] != null ? DateTime.tryParse(json['createdAt'].toString()) : null,
      updatedAt: json['updatedAt'] != null ? DateTime.tryParse(json['updatedAt'].toString()) : null,
      verifiedAt: json['verifiedAt'] != null ? DateTime.tryParse(json['verifiedAt'].toString()) : null,
      simulated: json['simulated'] != false,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'userId': userId,
      'linkedBankAccountId': linkedBankAccountId,
      'last4': last4,
      'expMonth': expMonth,
      'expYear': expYear,
      'brandLabel': brandLabel,
      'nickname': nickname,
      'cardKind': cardKind.toJson(),
      'status': status.toJson(),
      'onlinePaymentsEnabled': onlinePaymentsEnabled,
      'perPaymentLimitMinor': perPaymentLimitMinor,
      'dailyLimitMinor': dailyLimitMinor,
      'simulated': simulated,
    };
  }
}

class CardRevealModel {
  final int cardId;
  final String last4;
  final String pan;
  final String cvv;
  final String expMonth;
  final String expYear;
  final String brandLabel;
  final int expiresInSeconds;
  final bool simulated;

  const CardRevealModel({
    required this.cardId,
    required this.last4,
    required this.pan,
    required this.cvv,
    required this.expMonth,
    required this.expYear,
    this.brandLabel = 'TEST CARD',
    this.expiresInSeconds = 30,
    this.simulated = true,
  });

  factory CardRevealModel.fromJson(Map<String, dynamic> json) {
    return CardRevealModel(
      cardId: (json['cardId'] as num?)?.toInt() ?? (json['id'] as num?)?.toInt() ?? 0,
      last4: json['last4']?.toString() ?? '',
      pan: json['pan']?.toString() ?? '',
      cvv: json['cvv']?.toString() ?? '',
      expMonth: json['expMonth']?.toString() ?? '12',
      expYear: json['expYear']?.toString() ?? '27',
      brandLabel: json['brandLabel']?.toString() ?? 'TEST CARD',
      expiresInSeconds: (json['expiresInSeconds'] as num?)?.toInt() ?? 30,
      simulated: json['simulated'] != false,
    );
  }
}

class TokenizeResultModel {
  final String cardToken;
  final String last4;
  final String expMonth;
  final String expYear;
  final String brandLabel;
  final bool simulated;
  final DateTime? tokenExpiresAt;

  const TokenizeResultModel({
    required this.cardToken,
    required this.last4,
    required this.expMonth,
    required this.expYear,
    this.brandLabel = 'TEST CARD',
    this.simulated = true,
    this.tokenExpiresAt,
  });

  factory TokenizeResultModel.fromJson(Map<String, dynamic> json) {
    return TokenizeResultModel(
      cardToken: json['cardToken']?.toString() ?? '',
      last4: json['last4']?.toString() ?? '',
      expMonth: json['expMonth']?.toString() ?? '',
      expYear: json['expYear']?.toString() ?? '',
      brandLabel: json['brandLabel']?.toString() ?? 'TEST CARD',
      simulated: json['simulated'] != false,
      tokenExpiresAt: json['tokenExpiresAt'] != null
          ? DateTime.tryParse(json['tokenExpiresAt'].toString())
          : null,
    );
  }
}
