import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../domain/transfer_models.dart';

class TransferService {
  final Dio _dio;

  TransferService(this._dio);

  /// Lookup recipient by phone number.
  /// Anti-enumeration handling: HTTP 200 => FOUND, HTTP 404 => payload with resultType.
  Future<LookupResultModel> lookupRecipient(String phone) async {
    try {
      final response = await _dio.get(
        '/payment-identities/lookup',
        queryParameters: {'phone': phone},
      );
      return LookupResultModel.fromJson(Map<String, dynamic>.from(response.data as Map));
    } on DioException catch (e) {
      if (e.response != null && e.response?.data is Map) {
        return LookupResultModel.fromJson(Map<String, dynamic>.from(e.response!.data as Map));
      }
      rethrow;
    }
  }

  /// Request quote before initiating transfer
  Future<QuoteModel> getQuote({
    int? payeeId,
    String? payeePhone,
    required double amount,
    String currency = 'LKR',
    FundingSourceModel? fundingSource,
  }) async {
    final payload = <String, dynamic>{
      'amount': amount,
      'currency': currency,
    };
    if (payeeId != null) payload['payeeId'] = payeeId;
    if (payeePhone != null) payload['payeePhone'] = payeePhone;
    if (fundingSource != null) payload['fundingSource'] = fundingSource.toJson();

    final response = await _dio.post(
      '/transfers/quote',
      data: payload,
    );
    return QuoteModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  /// Initiate transfer with Idempotency-Key and Step-up PIN
  Future<TransferModel> initiateTransfer({
    required String idempotencyKey,
    required String pin,
    int? payeeId,
    String? payeePhone,
    required double amount,
    String currency = 'LKR',
    String? quoteReference,
    String? note,
    FundingSourceModel? fundingSource,
  }) async {
    final payload = <String, dynamic>{
      'amount': amount,
      'currency': currency,
      'pin': pin,
    };
    if (payeeId != null) payload['payeeId'] = payeeId;
    if (payeePhone != null) payload['payeePhone'] = payeePhone;
    if (quoteReference != null) payload['quoteReference'] = quoteReference;
    if (note != null && note.isNotEmpty) payload['note'] = note;
    if (fundingSource != null) payload['fundingSource'] = fundingSource.toJson();

    final response = await _dio.post(
      '/transfers',
      options: Options(
        headers: {
          'Idempotency-Key': idempotencyKey,
          'X-Step-Up-Pin': pin,
        },
      ),
      data: payload,
    );
    return TransferModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  /// Submit OTP challenge for card transfer
  Future<TransferModel> challengeTransfer({
    required int transferId,
    required String otp,
  }) async {
    final response = await _dio.post(
      '/transfers/$transferId/challenge',
      data: {'otp': otp},
    );
    return TransferModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  /// Poll / get transfer status by ID
  Future<TransferModel> getTransferById(int transferId) async {
    final response = await _dio.get('/transfers/$transferId');
    return TransferModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }
}

final transferServiceProvider = Provider<TransferService>((ref) {
  final dio = ref.watch(dioProvider);
  return TransferService(dio);
});
