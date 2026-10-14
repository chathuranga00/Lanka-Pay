import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

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
  static const String _userFullNameKey = 'lkp_user_full_name';

  Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
    String? userId,
    String? phone,
    String? fullName,
  }) async {
    try {
      await _storage.write(key: _accessTokenKey, value: accessToken);
      await _storage.write(key: _refreshTokenKey, value: refreshToken);
      if (userId != null) {
        await _storage.write(key: _userIdKey, value: userId);
      }
      if (phone != null) {
        await _storage.write(key: _userPhoneKey, value: phone);
      }
      if (fullName != null) {
        await _storage.write(key: _userFullNameKey, value: fullName);
      }
    } catch (_) {}

    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_accessTokenKey, accessToken);
      await prefs.setString(_refreshTokenKey, refreshToken);
      if (userId != null) await prefs.setString(_userIdKey, userId);
      if (phone != null) await prefs.setString(_userPhoneKey, phone);
      if (fullName != null) await prefs.setString(_userFullNameKey, fullName);
    } catch (_) {}
  }

  Future<String?> getAccessToken() async {
    try {
      final val = await _storage.read(key: _accessTokenKey);
      if (val != null && val.isNotEmpty) return val;
    } catch (_) {}
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getString(_accessTokenKey);
    } catch (_) {
      return null;
    }
  }

  Future<String?> getRefreshToken() async {
    try {
      final val = await _storage.read(key: _refreshTokenKey);
      if (val != null && val.isNotEmpty) return val;
    } catch (_) {}
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getString(_refreshTokenKey);
    } catch (_) {
      return null;
    }
  }

  Future<String?> getUserId() async {
    try {
      final val = await _storage.read(key: _userIdKey);
      if (val != null && val.isNotEmpty) return val;
    } catch (_) {}
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getString(_userIdKey);
    } catch (_) {
      return null;
    }
  }

  Future<String?> getUserPhone() async {
    try {
      final val = await _storage.read(key: _userPhoneKey);
      if (val != null && val.isNotEmpty) return val;
    } catch (_) {}
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getString(_userPhoneKey);
    } catch (_) {
      return null;
    }
  }

  Future<String?> getUserFullName() async {
    try {
      final val = await _storage.read(key: _userFullNameKey);
      if (val != null && val.isNotEmpty) return val;
    } catch (_) {}
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getString(_userFullNameKey);
    } catch (_) {
      return null;
    }
  }

  Future<void> clearAll() async {
    try {
      await _storage.deleteAll();
    } catch (_) {}
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_accessTokenKey);
      await prefs.remove(_refreshTokenKey);
      await prefs.remove(_userIdKey);
      await prefs.remove(_userPhoneKey);
      await prefs.remove(_userFullNameKey);
    } catch (_) {}
  }
}

final tokenStorageProvider = Provider<TokenStorage>((ref) {
  final storage = ref.watch(secureStorageProvider);
  return TokenStorage(storage);
});
