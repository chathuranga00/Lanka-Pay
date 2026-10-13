import 'dart:math';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../domain/split_models.dart';

final splitServiceProvider = Provider<SplitService>((ref) {
  final dio = ref.watch(dioProvider);
  return SplitService(dio);
});

class SplitService {
  final Dio _dio;

  SplitService(this._dio);

  Future<List<SplitModel>> listSplits() async {
    final response = await _dio.get('/api/v1/splits');
    final data = response.data as List;
    return data
        .map((e) => SplitModel.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  Future<SplitModel> getSplit(String id) async {
    final response = await _dio.get('/api/v1/splits/$id');
    return SplitModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<SplitModel> createSplit({
    required String title,
    required int totalMinor,
    String currency = 'LKR',
    String mode = 'EQUAL',
    required List<Map<String, dynamic>> participants,
    int validityHours = 48,
  }) async {
    final response = await _dio.post(
      '/api/v1/splits',
      data: {
        'title': title,
        'totalMinor': totalMinor,
        'currency': currency,
        'mode': mode,
        'participants': participants,
        'validityHours': validityHours,
      },
    );

    return SplitModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<SplitModel> cancelSplit(String id) async {
    final response = await _dio.post('/api/v1/splits/$id/cancel');
    return SplitModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<SplitModel> declineShare(String id) async {
    final response = await _dio.post('/api/v1/splits/$id/decline');
    return SplitModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<SplitModel> remindParticipant(String id, int participantId) async {
    final response = await _dio.post('/api/v1/splits/$id/remind/$participantId');
    return SplitModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<SplitModel> payShare(
    String id, {
    required String pin,
    String? fundingSource,
    String? idempotencyKey,
  }) async {
    final key = idempotencyKey ??
        'IDEMP-SPLIT-${DateTime.now().millisecondsSinceEpoch}-${Random().nextInt(999999)}';
    final response = await _dio.post(
      '/api/v1/splits/$id/pay',
      data: {
        'pin': pin,
        'fundingSource': fundingSource,
        'idempotencyKey': key,
      },
    );

    return SplitModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }
}
