import 'dart:math';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../domain/scheduled_models.dart';

final scheduledServiceProvider = Provider<ScheduledService>((ref) {
  final dio = ref.watch(dioProvider);
  return ScheduledService(dio);
});

class ScheduledService {
  final Dio _dio;

  ScheduledService(this._dio);

  Future<List<ScheduledPaymentModel>> listSchedules({
    ScheduleStatus? status,
  }) async {
    final queryParams = <String, dynamic>{};
    if (status != null) {
      queryParams['status'] = status.toBackendString();
    }

    final response = await _dio.get(
      '/api/v1/scheduled',
      queryParameters: queryParams.isNotEmpty ? queryParams : null,
    );

    final data = response.data as List;
    return data
        .map((e) => ScheduledPaymentModel.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  Future<ScheduledPaymentModel> getSchedule(String id) async {
    final response = await _dio.get('/api/v1/scheduled/$id');
    return ScheduledPaymentModel.fromJson(
      Map<String, dynamic>.from(response.data as Map),
    );
  }

  Future<ScheduledPaymentModel> createSchedule({
    required ScheduleType type,
    required Map<String, dynamic> payee,
    int? amountMinor,
    String currency = 'LKR',
    String? fundingSource,
    required ScheduleFrequency frequency,
    required String startDate,
    String? endDate,
    required String pin,
    required bool consentGiven,
    String? description,
    String? idempotencyKey,
  }) async {
    final key = idempotencyKey ??
        'IDEMP-SCHED-${DateTime.now().millisecondsSinceEpoch}-${Random().nextInt(999999)}';
    final headers = <String, dynamic>{
      'Idempotency-Key': key,
      'X-Step-Up-Pin': pin,
    };

    final payload = <String, dynamic>{
      'type': type.toBackendString(),
      'payee': payee,
      'currency': currency,
      'frequency': frequency.toBackendString(),
      'startDate': startDate,
      'consentGiven': consentGiven,
    };
    if (amountMinor != null) payload['amountMinor'] = amountMinor;
    if (fundingSource != null) payload['fundingSource'] = fundingSource;
    if (endDate != null && endDate.isNotEmpty) payload['endDate'] = endDate;
    if (description != null && description.isNotEmpty) payload['description'] = description;

    final response = await _dio.post(
      '/api/v1/scheduled',
      data: payload,
      options: Options(headers: headers),
    );

    return ScheduledPaymentModel.fromJson(
      Map<String, dynamic>.from(response.data as Map),
    );
  }

  Future<ScheduledPaymentModel> updateSchedule(
    String id, {
    int? amountMinor,
    Map<String, dynamic>? payee,
    ScheduleFrequency? frequency,
    String? endDate,
    ScheduleStatus? status,
    String? fundingSource,
    String? pin,
  }) async {
    final headers = <String, dynamic>{};
    if (pin != null && pin.isNotEmpty) {
      headers['X-Step-Up-Pin'] = pin;
    }

    final payload = <String, dynamic>{};
    if (amountMinor != null) payload['amountMinor'] = amountMinor;
    if (payee != null) payload['payee'] = payee;
    if (frequency != null) payload['frequency'] = frequency.toBackendString();
    if (endDate != null) payload['endDate'] = endDate;
    if (status != null) payload['status'] = status.toBackendString();
    if (fundingSource != null) payload['fundingSource'] = fundingSource;

    final response = await _dio.patch(
      '/api/v1/scheduled/$id',
      data: payload,
      options: headers.isNotEmpty ? Options(headers: headers) : null,
    );

    return ScheduledPaymentModel.fromJson(
      Map<String, dynamic>.from(response.data as Map),
    );
  }

  Future<void> pauseSchedule(String id) async {
    await updateSchedule(id, status: ScheduleStatus.paused);
  }

  Future<void> resumeSchedule(String id) async {
    await updateSchedule(id, status: ScheduleStatus.active);
  }

  Future<void> cancelSchedule(String id) async {
    await _dio.delete('/api/v1/scheduled/$id');
  }

  Future<List<ScheduledRunModel>> getRuns(String id) async {
    final response = await _dio.get('/api/v1/scheduled/$id/runs');
    final data = response.data as List;
    return data
        .map((e) => ScheduledRunModel.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }
}

// ---------------------------------------------------------------------------
// Providers & State Notifiers
// ---------------------------------------------------------------------------

class ScheduledListNotifier extends AsyncNotifier<List<ScheduledPaymentModel>> {
  @override
  Future<List<ScheduledPaymentModel>> build() async {
    final service = ref.watch(scheduledServiceProvider);
    return service.listSchedules();
  }

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final service = ref.read(scheduledServiceProvider);
      return service.listSchedules();
    });
  }

  Future<void> pause(String id) async {
    final service = ref.read(scheduledServiceProvider);
    await service.pauseSchedule(id);
    await refresh();
  }

  Future<void> resume(String id) async {
    final service = ref.read(scheduledServiceProvider);
    await service.resumeSchedule(id);
    await refresh();
  }

  Future<void> cancel(String id) async {
    final service = ref.read(scheduledServiceProvider);
    await service.cancelSchedule(id);
    await refresh();
  }
}

final scheduledListProvider =
    AsyncNotifierProvider<ScheduledListNotifier, List<ScheduledPaymentModel>>(
  ScheduledListNotifier.new,
);

final scheduledRunsProvider =
    FutureProvider.family<List<ScheduledRunModel>, String>((ref, id) async {
  final service = ref.watch(scheduledServiceProvider);
  return service.getRuns(id);
});
