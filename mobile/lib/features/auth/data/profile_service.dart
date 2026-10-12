import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../../../core/storage/token_storage.dart';

/// Fetches the authenticated user's real name from the backend.
/// Uses GET /api/v1/payment-profile/me (returns displayName + maskedPhone).
class ProfileService {
  final Dio _dio;
  final TokenStorage _tokenStorage;

  ProfileService(this._dio, this._tokenStorage);

  /// Returns the user's full name from the backend, or null if unavailable.
  /// Persists the name to secure storage so it survives app restarts.
  Future<String?> fetchAndCacheFullName() async {
    try {
      final response = await _dio.get('/payment-profile/me');
      final data = response.data as Map<String, dynamic>?;

      // PaymentProfileMeResponse has displayName (abbreviated) — we also
      // check fullName if the backend adds it, else fall back to displayName.
      final fullName = (data?['fullName'] as String?)
          ?? (data?['displayName'] as String?);

      if (fullName != null && fullName.isNotEmpty && fullName != 'User') {
        await _tokenStorage.saveTokens(
          accessToken: await _tokenStorage.getAccessToken() ?? '',
          refreshToken: await _tokenStorage.getRefreshToken() ?? '',
          fullName: fullName,
        );
        return fullName;
      }
    } on DioException {
      // 404 = phone not verified yet (user hasn't completed verification)
      // Any error: silently return null, app shows fallback
    } catch (_) {}
    return null;
  }
}

final profileServiceProvider = Provider<ProfileService>((ref) {
  return ProfileService(
    ref.watch(dioProvider),
    ref.watch(tokenStorageProvider),
  );
});
