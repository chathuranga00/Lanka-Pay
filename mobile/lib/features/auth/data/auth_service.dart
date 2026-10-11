import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../../../core/storage/token_storage.dart';
import '../domain/auth_state.dart';

/// Service managing remote authentication calls against the LankaPay backend
class AuthService {
  final Dio _dio;
  final TokenStorage _tokenStorage;

  AuthService(this._dio, this._tokenStorage);

  /// Normalizes a Sri Lankan phone number to E.164 (+947XXXXXXXX)
  static String normalizePhone(String raw) {
    var cleaned = raw.replaceAll(RegExp(r'[\s\-]'), '');
    if (cleaned.startsWith('0') && cleaned.length == 10) {
      cleaned = '+94${cleaned.substring(1)}';
    } else if (!cleaned.startsWith('+') && cleaned.startsWith('94')) {
      cleaned = '+$cleaned';
    }
    return cleaned;
  }

  /// Registers a new user. Returns userId and OTP expiry.
  Future<Map<String, dynamic>> register({
    required String phone,
    required String fullName,
    required String nic,
    required String pin,
    String role = 'CUSTOMER',
  }) async {
    final normalizedPhone = normalizePhone(phone);
    final response = await _dio.post(
      '/auth/register',
      data: {
        'phone': normalizedPhone,
        'fullName': fullName.trim(),
        'nic': nic.trim().toUpperCase(),
        'pin': pin.trim(),
        'role': role,
      },
    );

    return Map<String, dynamic>.from(response.data as Map);
  }

  /// Verifies the 6-digit registration OTP and stores the issued token pair.
  Future<Map<String, dynamic>> verifyOtp({
    required int userId,
    required String otp,
    required String phone,
  }) async {
    final response = await _dio.post(
      '/auth/verify-otp',
      data: {
        'userId': userId,
        'otp': otp.trim(),
      },
    );

    final data = Map<String, dynamic>.from(response.data as Map);
    final accessToken = data['accessToken'] as String?;
    final refreshToken = data['refreshToken'] as String?;

    if (accessToken != null && refreshToken != null) {
      await _tokenStorage.saveTokens(
        accessToken: accessToken,
        refreshToken: refreshToken,
        userId: userId.toString(),
        phone: normalizePhone(phone),
      );
    }

    return data;
  }

  /// Authenticates using phone and 6-digit PIN.
  Future<Map<String, dynamic>> login({
    required String phone,
    required String pin,
  }) async {
    final normalizedPhone = normalizePhone(phone);
    final response = await _dio.post(
      '/auth/login',
      data: {
        'phone': normalizedPhone,
        'pin': pin.trim(),
      },
    );

    final data = Map<String, dynamic>.from(response.data as Map);
    final accessToken = data['accessToken'] as String?;
    final refreshToken = data['refreshToken'] as String?;

    if (accessToken != null) {
      await _tokenStorage.saveTokens(
        accessToken: accessToken,
        refreshToken: refreshToken ?? '',
        phone: normalizedPhone,
      );
    }

    return data;
  }

  /// Revokes refresh token and clears all local credentials.
  Future<void> logout() async {
    try {
      final refreshToken = await _tokenStorage.getRefreshToken();
      await _dio.post(
        '/auth/logout',
        data: refreshToken != null ? {'refreshToken': refreshToken} : null,
      );
    } catch (_) {
      // Best-effort logout; continue to wipe local session
    } finally {
      await _tokenStorage.clearAll();
    }
  }

  /// Initiates password/PIN recovery workflow
  Future<Map<String, dynamic>> initiateRecovery({
    required String phone,
    required String nic,
  }) async {
    final normalizedPhone = normalizePhone(phone);
    // In prototype, recovery uses lookup and sends notification or OTP
    final response = await _dio.get(
      '/payment-identities/lookup',
      queryParameters: {'identifier': normalizedPhone},
    );

    return Map<String, dynamic>.from(response.data as Map);
  }
}

final authServiceProvider = Provider<AuthService>((ref) {
  final dio = ref.watch(dioProvider);
  final tokenStorage = ref.watch(tokenStorageProvider);
  return AuthService(dio, tokenStorage);
});

/// StateNotifier controlling the authentication state of the application
class AuthNotifier extends Notifier<AuthState> {
  late final AuthService _authService;
  late final TokenStorage _tokenStorage;

  @override
  AuthState build() {
    _authService = ref.watch(authServiceProvider);
    _tokenStorage = ref.watch(tokenStorageProvider);
    _checkInitialSession();
    return const AuthInitial();
  }

  Future<void> _checkInitialSession() async {
    final token = await _tokenStorage.getAccessToken();
    if (token != null && token.isNotEmpty) {
      final userId = await _tokenStorage.getUserId();
      final phone = await _tokenStorage.getUserPhone();
      state = AuthAuthenticated(
        accessToken: token,
        userId: userId,
        phone: phone,
      );
    } else {
      state = const AuthUnauthenticated();
    }
  }

  Future<bool> register({
    required String phone,
    required String fullName,
    required String nic,
    required String pin,
    String role = 'CUSTOMER',
  }) async {
    state = const AuthLoading(message: 'Creating account and sending OTP...');
    try {
      final res = await _authService.register(
        phone: phone,
        fullName: fullName,
        nic: nic,
        pin: pin,
        role: role,
      );

      final userId = res['userId']?.toString() ?? '';
      DateTime? expiresAt;
      if (res['otpExpiresAt'] != null) {
        expiresAt = DateTime.tryParse(res['otpExpiresAt'].toString());
      }

      state = AuthOtpPending(
        userId: userId,
        phone: AuthService.normalizePhone(phone),
        expiresAt: expiresAt,
      );
      return true;
    } on DioException catch (e) {
      state = _mapDioError(e);
      return false;
    } catch (e) {
      state = AuthError(message: e.toString());
      return false;
    }
  }

  Future<bool> verifyOtp({
    required int userId,
    required String otp,
    required String phone,
  }) async {
    state = const AuthLoading(message: 'Verifying OTP code...');
    try {
      final res = await _authService.verifyOtp(
        userId: userId,
        otp: otp,
        phone: phone,
      );

      state = AuthAuthenticated(
        accessToken: res['accessToken'] as String? ?? '',
        refreshToken: res['refreshToken'] as String?,
        userId: userId.toString(),
        phone: phone,
      );
      return true;
    } on DioException catch (e) {
      state = _mapDioError(e);
      return false;
    } catch (e) {
      state = AuthError(message: e.toString());
      return false;
    }
  }

  Future<bool> login({
    required String phone,
    required String pin,
  }) async {
    state = const AuthLoading(message: 'Authenticating securely...');
    try {
      final res = await _authService.login(
        phone: phone,
        pin: pin,
      );

      state = AuthAuthenticated(
        accessToken: res['accessToken'] as String? ?? '',
        refreshToken: res['refreshToken'] as String?,
        phone: AuthService.normalizePhone(phone),
      );
      return true;
    } on DioException catch (e) {
      state = _mapDioError(e);
      return false;
    } catch (e) {
      state = AuthError(message: e.toString());
      return false;
    }
  }

  Future<void> logout() async {
    state = const AuthLoading(message: 'Signing out...');
    await _authService.logout();
    state = const AuthUnauthenticated();
  }

  void resetError() {
    if (state is AuthError) {
      state = const AuthUnauthenticated();
    }
  }

  AuthError _mapDioError(DioException e) {
    final data = e.response?.data;
    if (data is Map) {
      final message = data['message'] as String? ?? 'An unexpected error occurred.';
      final code = data['code'] as String?;
      final errors = data['errors'] is Map ? Map<String, dynamic>.from(data['errors'] as Map) : null;
      return AuthError(
        message: message,
        errorCode: code,
        fieldErrors: errors,
      );
    }
    return AuthError(
      message: e.message ?? 'Network connection failed. Please check your internet.',
    );
  }
}

final authStateProvider = NotifierProvider<AuthNotifier, AuthState>(() {
  return AuthNotifier();
});
