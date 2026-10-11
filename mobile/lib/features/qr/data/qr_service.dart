import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../domain/qr_models.dart';

class QrService {
  final Dio _dio;

  QrService(this._dio);

  /// Generate a static personal payment QR reference
  Future<QrReferenceModel> generateStaticQr() async {
    final response = await _dio.post(
      '/qr/references',
      data: {
        'qrType': 'STATIC',
      },
    );
    return QrReferenceModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  /// Generate dynamic payment request QR with amount and expiry
  Future<QrReferenceModel> generateDynamicQr({
    required double amount,
    int validityMinutes = 15,
    String? note,
    String currency = 'LKR',
  }) async {
    final payload = <String, dynamic>{
      'qrType': 'DYNAMIC',
      'amount': amount,
      'validityMinutes': validityMinutes,
      'currency': currency,
    };
    if (note != null && note.isNotEmpty) payload['note'] = note;

    final response = await _dio.post(
      '/qr/references',
      data: payload,
    );
    return QrReferenceModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  /// Scan / resolve an opaque QR reference
  Future<ResolvedQrModel> resolveReference(String reference) async {
    // If scanning payload like LANKAPAY:QR:QREF-xxx, strip prefix if present
    String ref = reference;
    if (ref.contains('QREF-')) {
      final startIndex = ref.indexOf('QREF-');
      ref = ref.substring(startIndex);
    }

    final response = await _dio.get('/qr/references/$ref');
    return ResolvedQrModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }
}

final qrServiceProvider = Provider<QrService>((ref) {
  final dio = ref.watch(dioProvider);
  return QrService(dio);
});
