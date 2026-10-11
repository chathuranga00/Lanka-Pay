import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../domain/bank_account_model.dart';

class BankAccountService {
  final Dio _dio;

  BankAccountService(this._dio);

  Future<List<BankInstitutionModel>> listInstitutions() async {
    final response = await _dio.get('/bank-institutions');
    final data = response.data as List;
    return data.map((e) => BankInstitutionModel.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  Future<List<BankAccountModel>> listAccounts() async {
    final response = await _dio.get('/bank-accounts');
    final data = response.data as List;
    return data
        .map((e) => BankAccountModel.fromJson(Map<String, dynamic>.from(e as Map)))
        .where((a) => a.status != 'REMOVED')
        .toList();
  }

  Future<BankAccountModel> linkAccount({
    required String institutionCode,
    required String accountNumber,
    required String accountName,
    String? label,
    bool consentGiven = true,
  }) async {
    final response = await _dio.post(
      '/bank-accounts/link',
      data: {
        'institutionCode': institutionCode,
        'accountNumber': accountNumber,
        'accountName': accountName,
        'label': label,
        'consentGiven': consentGiven,
      },
    );
    return BankAccountModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<BankAccountModel> patchAccount({
    required int id,
    String? label,
    bool? discoverable,
    bool? receivingEnabled,
  }) async {
    final data = <String, dynamic>{};
    if (label != null) data['label'] = label;
    if (discoverable != null) data['discoverable'] = discoverable;
    if (receivingEnabled != null) data['receivingEnabled'] = receivingEnabled;

    final response = await _dio.patch(
      '/bank-accounts/$id',
      data: data,
    );
    return BankAccountModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<BankAccountModel> setDefault(int id) async {
    final response = await _dio.post('/bank-accounts/$id/set-default');
    return BankAccountModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<void> removeAccount(int id) async {
    await _dio.delete(
      '/bank-accounts/$id',
      data: {'consentGiven': true},
    );
  }
}

final bankAccountServiceProvider = Provider<BankAccountService>((ref) {
  final dio = ref.watch(dioProvider);
  return BankAccountService(dio);
});

class BankAccountsNotifier extends AsyncNotifier<List<BankAccountModel>> {
  @override
  Future<List<BankAccountModel>> build() async {
    return _fetch();
  }

  Future<List<BankAccountModel>> _fetch() async {
    final service = ref.read(bankAccountServiceProvider);
    return await service.listAccounts();
  }

  Future<void> refresh() async {
    state = const AsyncValue.loading();
    state = await AsyncValue.guard(() => _fetch());
  }

  Future<bool> linkAccount({
    required String institutionCode,
    required String accountNumber,
    required String accountName,
    String? label,
  }) async {
    try {
      final service = ref.read(bankAccountServiceProvider);
      await service.linkAccount(
        institutionCode: institutionCode,
        accountNumber: accountNumber,
        accountName: accountName,
        label: label,
      );
      await refresh();
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<void> setDefault(int id) async {
    final service = ref.read(bankAccountServiceProvider);
    await service.setDefault(id);
    await refresh();
  }

  Future<void> patchAccount({
    required int id,
    String? label,
    bool? discoverable,
    bool? receivingEnabled,
  }) async {
    final service = ref.read(bankAccountServiceProvider);
    await service.patchAccount(
      id: id,
      label: label,
      discoverable: discoverable,
      receivingEnabled: receivingEnabled,
    );
    await refresh();
  }

  Future<void> removeAccount(int id) async {
    final service = ref.read(bankAccountServiceProvider);
    await service.removeAccount(id);
    await refresh();
  }
}

final bankAccountsProvider = AsyncNotifierProvider<BankAccountsNotifier, List<BankAccountModel>>(() {
  return BankAccountsNotifier();
});
