import 'dart:math';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../domain/bill_models.dart';

final billServiceProvider = Provider<BillService>((ref) {
  final dio = ref.watch(dioProvider);
  return BillService(dio);
});

class BillService {
  final Dio _dio;

  BillService(this._dio);

  Future<List<BillerModel>> listBillers({
    BillerCategory? category,
    String? query,
  }) async {
    final queryParams = <String, dynamic>{};
    if (category != null) {
      queryParams['category'] = category.toBackendString();
    }
    if (query != null && query.trim().isNotEmpty) {
      queryParams['query'] = query.trim();
    }

    final response = await _dio.get(
      '/billers',
      queryParameters: queryParams.isNotEmpty ? queryParams : null,
    );

    final data = response.data as List;
    return data
        .map((e) => BillerModel.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  Future<BillerModel> getBiller(String id) async {
    final response = await _dio.get('/billers/$id');
    return BillerModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<BillLookupModel> lookupBill({
    required String billerId,
    required String customerReference,
  }) async {
    final response = await _dio.post(
      '/bills/lookup',
      data: {
        'billerId': billerId,
        'customerReference': customerReference,
      },
    );
    return BillLookupModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<BillPaymentReceiptModel> payBill({
    required String billerId,
    required String customerReference,
    required double amount,
    required String fundingType, // ACCOUNT or CARD
    required int fundingId,
    required String pin,
    String currency = 'LKR',
    String? note,
    bool saveBiller = false,
    String? billerNickname,
    String? idempotencyKey,
  }) async {
    final key = idempotencyKey ??
        'IDEMP-${DateTime.now().millisecondsSinceEpoch}-${Random().nextInt(999999)}';
    final headers = <String, dynamic>{
      'Idempotency-Key': key,
      'X-Step-Up-Pin': pin,
    };

    final response = await _dio.post(
      '/bills/pay',
      options: Options(headers: headers),
      data: {
        'billerId': billerId,
        'customerReference': customerReference,
        'amount': amount,
        'currency': currency,
        'fundingSource': {
          'type': fundingType.toUpperCase(),
          'id': fundingId,
        },
        'pin': pin,
        'note': note,
        'saveBiller': saveBiller,
        'billerNickname': billerNickname,
      },
    );

    return BillPaymentReceiptModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<List<BillPaymentReceiptModel>> listPayments({
    int offset = 0,
    int limit = 50,
  }) async {
    final response = await _dio.get(
      '/bills/payments',
      queryParameters: {
        'offset': offset,
        'limit': limit,
      },
    );
    final data = response.data as List;
    return data
        .map((e) => BillPaymentReceiptModel.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  Future<BillPaymentReceiptModel> getPayment(int id) async {
    final response = await _dio.get('/bills/payments/$id');
    return BillPaymentReceiptModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<BillPaymentReceiptModel> reconcilePayment(int id) async {
    final response = await _dio.post('/bills/payments/$id/reconcile');
    return BillPaymentReceiptModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<List<SavedBillerModel>> listSavedBillers() async {
    final response = await _dio.get('/saved-billers');
    final data = response.data as List;
    return data
        .map((e) => SavedBillerModel.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  Future<SavedBillerModel> saveBiller({
    required String billerId,
    required String nickname,
    required String customerReference,
  }) async {
    final response = await _dio.post(
      '/saved-billers',
      data: {
        'billerId': billerId,
        'nickname': nickname,
        'customerReference': customerReference,
      },
    );
    return SavedBillerModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<SavedBillerModel> updateSavedBiller({
    required int id,
    required String nickname,
    String? customerReference,
  }) async {
    final data = <String, dynamic>{'nickname': nickname};
    if (customerReference != null) {
      data['customerReference'] = customerReference;
    }
    final response = await _dio.put(
      '/saved-billers/$id',
      data: data,
    );
    return SavedBillerModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<void> deleteSavedBiller(int id) async {
    await _dio.delete('/saved-billers/$id');
  }
}

// ---------------------------------------------------------------------------
// Providers & State Notifiers
// ---------------------------------------------------------------------------

class SelectedBillerCategoryNotifier extends Notifier<BillerCategory?> {
  @override
  BillerCategory? build() => null;

  void select(BillerCategory? category) {
    state = category;
  }
}

final selectedBillerCategoryProvider =
    NotifierProvider<SelectedBillerCategoryNotifier, BillerCategory?>(
  SelectedBillerCategoryNotifier.new,
);

class BillerSearchQueryNotifier extends Notifier<String> {
  @override
  String build() => '';

  void setQuery(String query) {
    state = query;
  }
}

final billerSearchQueryProvider =
    NotifierProvider<BillerSearchQueryNotifier, String>(
  BillerSearchQueryNotifier.new,
);

final billersProvider = FutureProvider<List<BillerModel>>((ref) async {
  final service = ref.watch(billServiceProvider);
  final category = ref.watch(selectedBillerCategoryProvider);
  final query = ref.watch(billerSearchQueryProvider);
  return service.listBillers(category: category, query: query);
});

class SavedBillersNotifier extends AsyncNotifier<List<SavedBillerModel>> {
  @override
  Future<List<SavedBillerModel>> build() async {
    final service = ref.watch(billServiceProvider);
    return service.listSavedBillers();
  }

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final service = ref.read(billServiceProvider);
      return service.listSavedBillers();
    });
  }

  Future<SavedBillerModel> addSavedBiller({
    required String billerId,
    required String nickname,
    required String customerReference,
  }) async {
    final service = ref.read(billServiceProvider);
    final saved = await service.saveBiller(
      billerId: billerId,
      nickname: nickname,
      customerReference: customerReference,
    );
    final current = state.value ?? [];
    state = AsyncData([...current, saved]);
    return saved;
  }

  Future<SavedBillerModel> updateNickname(int id, String nickname) async {
    final service = ref.read(billServiceProvider);
    final updated = await service.updateSavedBiller(id: id, nickname: nickname);
    final current = state.value ?? [];
    state = AsyncData(
      current.map((item) => item.id == id ? updated : item).toList(),
    );
    return updated;
  }

  Future<void> deleteSavedBiller(int id) async {
    final service = ref.read(billServiceProvider);
    await service.deleteSavedBiller(id);
    final current = state.value ?? [];
    state = AsyncData(
      current.where((item) => item.id != id).toList(),
    );
  }
}

final savedBillersProvider =
    AsyncNotifierProvider<SavedBillersNotifier, List<SavedBillerModel>>(
  SavedBillersNotifier.new,
);

final billPaymentsProvider = FutureProvider<List<BillPaymentReceiptModel>>((ref) async {
  final service = ref.watch(billServiceProvider);
  return service.listPayments();
});
