import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/config/app_config.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/auth_interceptor.dart';
import '../../../core/storage/token_storage.dart';
import '../domain/card_model.dart';

/// Dedicated Dio instance for SimVault tokenization.
/// CRITICAL PCI RULE: MUST NOT have LogInterceptor attached.
/// Never logs PAN, CVV, or card parameters.
final vaultDioProvider = Provider<Dio>((ref) {
  final tokenStorage = ref.watch(tokenStorageProvider);

  // Compute root URL (SimVault endpoint is at /sim-vault/v1/tokenize)
  final apiBase = AppConfig.apiBaseUrl;
  final rootBase = apiBase.endsWith('/api/v1')
      ? apiBase.substring(0, apiBase.length - '/api/v1'.length)
      : apiBase;

  final dio = Dio(
    BaseOptions(
      baseUrl: rootBase,
      connectTimeout: Duration(milliseconds: AppConfig.connectTimeoutMs),
      receiveTimeout: Duration(milliseconds: AppConfig.receiveTimeoutMs),
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
      },
    ),
  );

  // Attach auth interceptor so requests are authenticated with user's JWT.
  // Note: Absolutely NO LogInterceptor!
  dio.interceptors.add(AuthInterceptor(tokenStorage, dio));

  return dio;
});

class CardService {
  final Dio dio;
  final Dio vaultDio;

  CardService({required this.dio, required this.vaultDio});

  /// Tokenize sensitive card entry directly with SimVault.
  /// Uses dedicated un-logged Dio instance.
  Future<TokenizeResultModel> tokenize({
    required String pan,
    required String expMonth,
    required String expYear,
    required String cvv,
    required String holderName,
  }) async {
    try {
      final response = await vaultDio.post(
        '/sim-vault/v1/tokenize',
        data: {
          'pan': pan.replaceAll(RegExp(r'\D'), ''),
          'expMonth': expMonth.padLeft(2, '0'),
          'expYear': expYear.length == 4 ? expYear.substring(2) : expYear.padLeft(2, '0'),
          'cvv': cvv.trim(),
          'holderName': holderName.trim(),
        },
      );

      final data = Map<String, dynamic>.from(response.data as Map);
      return TokenizeResultModel.fromJson(data);
    } on DioException catch (e) {
      if (e.response?.statusCode == 422 || e.response?.statusCode == 400) {
        final data = e.response?.data;
        if (data is Map && data['message'] != null) {
          throw Exception(data['message'].toString());
        }
        throw Exception('Test cards only. Never enter a real card number.');
      }
      throw Exception(e.message ?? 'Card tokenization failed');
    }
  }

  /// List all cards owned by the authenticated user.
  Future<List<CardModel>> listCards() async {
    final response = await dio.get('/cards');
    final data = response.data as List;
    return data
        .map((e) => CardModel.fromJson(Map<String, dynamic>.from(e as Map)))
        .where((c) => c.status != CardStatus.removed)
        .toList();
  }

  /// Get single card by id.
  Future<CardModel> getCard(int id) async {
    final response = await dio.get('/cards/$id');
    return CardModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  /// Link an existing card using opaque vault token.
  Future<CardModel> linkCard({
    required String cardToken,
    String? nickname,
    int? linkedBankAccountId,
    bool consentGiven = true,
    String? pin,
  }) async {
    final body = <String, dynamic>{
      'cardToken': cardToken,
      'consentGiven': consentGiven,
    };
    if (nickname != null && nickname.isNotEmpty) {
      body['nickname'] = nickname;
    }
    if (linkedBankAccountId != null) {
      body['linkedBankAccountId'] = linkedBankAccountId;
    }
    if (pin != null && pin.isNotEmpty) {
      body['pin'] = pin;
    }

    final headers = <String, dynamic>{};
    if (pin != null && pin.isNotEmpty) {
      headers['X-Step-Up-Pin'] = pin;
    }

    final response = await dio.post(
      '/cards',
      data: body,
      options: headers.isNotEmpty ? Options(headers: headers) : null,
    );
    return CardModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  /// Issue a new virtual card tied to a linked bank account.
  Future<CardModel> issueVirtualCard({
    required int linkedBankAccountId,
    String? nickname,
    String? pin,
  }) async {
    final body = <String, dynamic>{
      'linkedBankAccountId': linkedBankAccountId,
    };
    if (nickname != null && nickname.isNotEmpty) {
      body['nickname'] = nickname;
    }
    if (pin != null && pin.isNotEmpty) {
      body['pin'] = pin;
    }

    final headers = <String, dynamic>{};
    if (pin != null && pin.isNotEmpty) {
      headers['X-Step-Up-Pin'] = pin;
    }

    final response = await dio.post(
      '/cards/virtual',
      data: body,
      options: headers.isNotEmpty ? Options(headers: headers) : null,
    );
    return CardModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  /// Verify ownership challenge using 6-digit bank OTP.
  Future<CardModel> verifyCard({
    required int id,
    required String otp,
  }) async {
    try {
      final response = await dio.post(
        '/cards/$id/verify',
        data: {'otp': otp.trim()},
      );
      return CardModel.fromJson(Map<String, dynamic>.from(response.data as Map));
    } on DioException catch (e) {
      if (e.response?.statusCode == 400 || e.response?.statusCode == 429) {
        final data = e.response?.data;
        if (data is Map && data['message'] != null) {
          throw Exception(data['message'].toString());
        }
      }
      rethrow;
    }
  }

  /// Temporarily freeze a card.
  Future<CardModel> freezeCard(int id) async {
    final response = await dio.post('/cards/$id/freeze');
    return CardModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  /// Unfreeze a frozen card with step-up PIN authentication.
  Future<CardModel> unfreezeCard(int id, {String? pin}) async {
    final headers = <String, dynamic>{};
    if (pin != null && pin.isNotEmpty) {
      headers['X-Step-Up-Pin'] = pin;
    }
    final response = await dio.post(
      '/cards/$id/unfreeze',
      queryParameters: pin != null ? {'pin': pin} : null,
      options: headers.isNotEmpty ? Options(headers: headers) : null,
    );
    return CardModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  /// Reveal virtual card details (PAN, CVV, expiry).
  /// Ephemeral (30s) and strictly for virtual cards.
  Future<CardRevealModel> revealCard(int id, {String? pin}) async {
    final headers = <String, dynamic>{};
    if (pin != null && pin.isNotEmpty) {
      headers['X-Step-Up-Pin'] = pin;
    }
    final response = await dio.post(
      '/cards/$id/reveal',
      queryParameters: pin != null ? {'pin': pin} : null,
      options: headers.isNotEmpty ? Options(headers: headers) : null,
    );
    return CardRevealModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  /// Update card settings and limits.
  Future<CardModel> patchCard(
    int id, {
    String? nickname,
    int? perPaymentLimitMinor,
    int? dailyLimitMinor,
    bool? onlinePaymentsEnabled,
    String? pin,
  }) async {
    final body = <String, dynamic>{};
    if (nickname != null) body['nickname'] = nickname;
    if (perPaymentLimitMinor != null) body['perPaymentLimitMinor'] = perPaymentLimitMinor;
    if (dailyLimitMinor != null) body['dailyLimitMinor'] = dailyLimitMinor;
    if (onlinePaymentsEnabled != null) body['onlinePaymentsEnabled'] = onlinePaymentsEnabled;
    if (pin != null) body['pin'] = pin;

    final headers = <String, dynamic>{};
    if (pin != null && pin.isNotEmpty) {
      headers['X-Step-Up-Pin'] = pin;
    }

    final response = await dio.patch(
      '/cards/$id',
      data: body,
      options: headers.isNotEmpty ? Options(headers: headers) : null,
    );
    return CardModel.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  /// Soft-delete / remove card.
  Future<void> removeCard(int id) async {
    await dio.delete('/cards/$id');
  }
}

final cardServiceProvider = Provider<CardService>((ref) {
  final dio = ref.watch(dioProvider);
  final vaultDio = ref.watch(vaultDioProvider);
  return CardService(dio: dio, vaultDio: vaultDio);
});

class CardsNotifier extends AsyncNotifier<List<CardModel>> {
  @override
  Future<List<CardModel>> build() async {
    return _fetch();
  }

  Future<List<CardModel>> _fetch() async {
    final service = ref.read(cardServiceProvider);
    return await service.listCards();
  }

  Future<void> refresh() async {
    state = const AsyncValue.loading();
    state = await AsyncValue.guard(() => _fetch());
  }

  Future<CardModel> linkCard({
    required String cardToken,
    String? nickname,
    int? linkedBankAccountId,
    bool consentGiven = true,
    String? pin,
  }) async {
    final service = ref.read(cardServiceProvider);
    final newCard = await service.linkCard(
      cardToken: cardToken,
      nickname: nickname,
      linkedBankAccountId: linkedBankAccountId,
      consentGiven: consentGiven,
      pin: pin,
    );
    final current = state.value ?? [];
    state = AsyncValue.data([...current, newCard]);
    return newCard;
  }

  Future<CardModel> issueVirtualCard({
    required int linkedBankAccountId,
    String? nickname,
    String? pin,
  }) async {
    final service = ref.read(cardServiceProvider);
    final newCard = await service.issueVirtualCard(
      linkedBankAccountId: linkedBankAccountId,
      nickname: nickname,
      pin: pin,
    );
    final current = state.value ?? [];
    state = AsyncValue.data([...current, newCard]);
    return newCard;
  }

  Future<CardModel> verifyCard({
    required int id,
    required String otp,
  }) async {
    final service = ref.read(cardServiceProvider);
    final verified = await service.verifyCard(id: id, otp: otp);
    final current = state.value ?? [];
    state = AsyncValue.data(
      current.map((c) => c.id == id ? verified : c).toList(),
    );
    return verified;
  }

  Future<CardModel> freezeCard(int id) async {
    final service = ref.read(cardServiceProvider);
    final updated = await service.freezeCard(id);
    final current = state.value ?? [];
    state = AsyncValue.data(
      current.map((c) => c.id == id ? updated : c).toList(),
    );
    return updated;
  }

  Future<CardModel> unfreezeCard(int id, {String? pin}) async {
    final service = ref.read(cardServiceProvider);
    final updated = await service.unfreezeCard(id, pin: pin);
    final current = state.value ?? [];
    state = AsyncValue.data(
      current.map((c) => c.id == id ? updated : c).toList(),
    );
    return updated;
  }

  Future<CardModel> patchCard(
    int id, {
    String? nickname,
    int? perPaymentLimitMinor,
    int? dailyLimitMinor,
    bool? onlinePaymentsEnabled,
    String? pin,
  }) async {
    final service = ref.read(cardServiceProvider);
    final updated = await service.patchCard(
      id,
      nickname: nickname,
      perPaymentLimitMinor: perPaymentLimitMinor,
      dailyLimitMinor: dailyLimitMinor,
      onlinePaymentsEnabled: onlinePaymentsEnabled,
      pin: pin,
    );
    final current = state.value ?? [];
    state = AsyncValue.data(
      current.map((c) => c.id == id ? updated : c).toList(),
    );
    return updated;
  }

  Future<void> removeCard(int id) async {
    final service = ref.read(cardServiceProvider);
    await service.removeCard(id);
    final current = state.value ?? [];
    state = AsyncValue.data(
      current.where((c) => c.id != id).toList(),
    );
  }
}

final cardsProvider = AsyncNotifierProvider<CardsNotifier, List<CardModel>>(CardsNotifier.new);
