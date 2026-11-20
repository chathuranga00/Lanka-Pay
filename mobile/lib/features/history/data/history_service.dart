import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../../send/domain/transfer_models.dart';

class ReceiptPartyModel {
  final int? id;
  final String? displayName;
  final String? maskedPhone;
  final String? bankCode;

  const ReceiptPartyModel({
    this.id,
    this.displayName,
    this.maskedPhone,
    this.bankCode,
  });

  factory ReceiptPartyModel.fromJson(Map<String, dynamic> json) {
    return ReceiptPartyModel(
      id: json['id'] as int?,
      displayName: json['displayName'] as String?,
      maskedPhone: json['maskedPhone'] as String?,
      bankCode: json['bankCode'] as String?,
    );
  }
}

class ReceiptModel {
  final String receiptNumber;
  final int transferId;
  final String reference;
  final String? bankTxRef;
  final String status;
  final double amount;
  final String currency;
  final double fee;
  final double totalDebited;
  final ReceiptPartyModel? sender;
  final ReceiptPartyModel? receiver;
  final String? note;
  final String? createdAt;
  final String? completedAt;
  final bool simulated;
  final String? digitalSignature;
  final FundingType? fundingType;
  final int? cardId;
  final String? cardLast4;
  final String? cardKind;
  final String? paymentMethod;

  const ReceiptModel({
    required this.receiptNumber,
    required this.transferId,
    required this.reference,
    this.bankTxRef,
    required this.status,
    required this.amount,
    this.currency = 'LKR',
    this.fee = 0.0,
    required this.totalDebited,
    this.sender,
    this.receiver,
    this.note,
    this.createdAt,
    this.completedAt,
    this.simulated = true,
    this.digitalSignature,
    this.fundingType,
    this.cardId,
    this.cardLast4,
    this.cardKind,
    this.paymentMethod,
  });

  factory ReceiptModel.fromJson(Map<String, dynamic> json) {
    return ReceiptModel(
      receiptNumber: json['receiptNumber'] as String? ?? '',
      transferId: json['transferId'] is int
          ? json['transferId'] as int
          : int.tryParse(json['transferId']?.toString() ?? '0') ?? 0,
      reference: json['reference'] as String? ?? '',
      bankTxRef: json['bankTxRef'] as String?,
      status: json['status'] as String? ?? 'COMPLETED',
      amount: (json['amount'] as num?)?.toDouble() ?? 0.0,
      currency: json['currency'] as String? ?? 'LKR',
      fee: (json['fee'] as num?)?.toDouble() ?? 0.0,
      totalDebited: (json['totalDebited'] as num?)?.toDouble() ?? 0.0,
      sender: json['sender'] is Map
          ? ReceiptPartyModel.fromJson(Map<String, dynamic>.from(json['sender'] as Map))
          : null,
      receiver: json['receiver'] is Map
          ? ReceiptPartyModel.fromJson(Map<String, dynamic>.from(json['receiver'] as Map))
          : null,
      note: json['note'] as String?,
      createdAt: json['createdAt'] as String?,
      completedAt: json['completedAt'] as String?,
      simulated: json['simulated'] as bool? ?? true,
      digitalSignature: json['digitalSignature'] as String?,
      fundingType: json['fundingType'] != null ? FundingType.fromString(json['fundingType'] as String?) : null,
      cardId: json['cardId'] is int ? json['cardId'] as int : int.tryParse(json['cardId']?.toString() ?? ''),
      cardLast4: json['cardLast4'] as String?,
      cardKind: json['cardKind']?.toString(),
      paymentMethod: json['paymentMethod'] as String?,
    );
  }
}

class HistoryFilter {
  final String? direction; // SENT or RECEIVED
  final String? status; // COMPLETED, PENDING, FAILED, etc.
  final String? bank;
  final double? minAmount;
  final double? maxAmount;
  final DateTime? fromDate;
  final DateTime? toDate;
  final String? fundingType; // ALL, ACCOUNT, CARD

  const HistoryFilter({
    this.direction,
    this.status,
    this.bank,
    this.minAmount,
    this.maxAmount,
    this.fromDate,
    this.toDate,
    this.fundingType,
  });

  HistoryFilter copyWith({
    String? direction,
    String? status,
    String? bank,
    double? minAmount,
    double? maxAmount,
    DateTime? fromDate,
    DateTime? toDate,
    String? fundingType,
    bool clearDirection = false,
    bool clearStatus = false,
    bool clearBank = false,
    bool clearFundingType = false,
  }) {
    return HistoryFilter(
      direction: clearDirection ? null : (direction ?? this.direction),
      status: clearStatus ? null : (status ?? this.status),
      bank: clearBank ? null : (bank ?? this.bank),
      minAmount: minAmount ?? this.minAmount,
      maxAmount: maxAmount ?? this.maxAmount,
      fromDate: fromDate ?? this.fromDate,
      toDate: toDate ?? this.toDate,
      fundingType: clearFundingType ? null : (fundingType ?? this.fundingType),
    );
  }
}

class HistoryService {
  final Dio _dio;

  HistoryService(this._dio);

  Future<List<TransferModel>> listHistory({
    HistoryFilter? filter,
    int page = 0,
    int size = 20,
  }) async {
    final queryParams = <String, dynamic>{
      'page': page,
      'size': size,
    };
    if (filter != null) {
      if (filter.direction != null && filter.direction!.isNotEmpty) {
        queryParams['direction'] = filter.direction;
      }
      if (filter.status != null && filter.status!.isNotEmpty) {
        queryParams['status'] = filter.status;
      }
      if (filter.bank != null && filter.bank!.isNotEmpty) {
        queryParams['bank'] = filter.bank;
      }
      if (filter.minAmount != null) {
        queryParams['minAmount'] = filter.minAmount;
      }
      if (filter.maxAmount != null) {
        queryParams['maxAmount'] = filter.maxAmount;
      }
      if (filter.fromDate != null) {
        queryParams['from'] = filter.fromDate!.toUtc().toIso8601String();
      }
      if (filter.toDate != null) {
        queryParams['to'] = filter.toDate!.toUtc().toIso8601String();
      }
    }

    final response = await _dio.get('/transfers', queryParameters: queryParams);
    final data = response.data;
    List items = [];
    if (data is Map && data['content'] is List) {
      items = data['content'] as List;
    } else if (data is List) {
      items = data;
    }

    var list = items
        .map((e) => TransferModel.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();

    if (filter != null && filter.fundingType != null && filter.fundingType != 'ALL') {
      final target = filter.fundingType!.toUpperCase();
      list = list.where((t) {
        if (target == 'CARD') {
          return t.fundingType == FundingType.card;
        } else if (target == 'ACCOUNT') {
          return t.fundingType == null || t.fundingType == FundingType.account;
        }
        return true;
      }).toList();
    }

    return list;
  }

  Future<ReceiptModel> getReceipt(int transferId) async {
    final response = await _dio.get('/transfers/$transferId/receipt');
    return ReceiptModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }
}

final historyServiceProvider = Provider<HistoryService>((ref) {
  final dio = ref.watch(dioProvider);
  return HistoryService(dio);
});
