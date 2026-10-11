import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Provider for FlutterSecureStorage
final secureStorageProvider = Provider<FlutterSecureStorage>((ref) {
  return const FlutterSecureStorage(
    aOptions: AndroidOptions(),
    iOptions: IOSOptions(
      accessibility: KeychainAccessibility.first_unlock,
    ),
  );
});

/// Key-value storage repository for authentication tokens and credentials.
class TokenStorage {
  final FlutterSecureStorage _storage;

  TokenStorage(this._storage);

  static const String _accessTokenKey = 'lkp_access_token';
  static const String _refreshTokenKey = 'lkp_refresh_token';
  static const String _userIdKey = 'lkp_user_id';
  static const String _userPhoneKey = 'lkp_user_phone';

  Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
    String? userId,
    String? phone,
  }) async {
    await _storage.write(key: _accessTokenKey, value: accessToken);
    await _storage.write(key: _refreshTokenKey, value: refreshToken);
    if (userId != null) {
      await _storage.write(key: _userIdKey, value: userId);
    }
    if (phone != null) {
      await _storage.write(key: _userPhoneKey, value: phone);
    }
  }

  Future<String?> getAccessToken() async {
    return await _storage.read(key: _accessTokenKey);
  }

  Future<String?> getRefreshToken() async {
    return await _storage.read(key: _refreshTokenKey);
  }

  Future<String?> getUserId() async {
    return await _storage.read(key: _userIdKey);
  }

  Future<String?> getUserPhone() async {
    return await _storage.read(key: _userPhoneKey);
  }

  Future<void> clearAll() async {
    await _storage.deleteAll();
  }
}

final tokenStorageProvider = Provider<TokenStorage>((ref) {
  final storage = ref.watch(secureStorageProvider);
  return TokenStorage(storage);
});
