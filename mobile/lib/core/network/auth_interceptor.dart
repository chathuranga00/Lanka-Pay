import 'package:dio/dio.dart';
import '../storage/token_storage.dart';
import '../config/app_config.dart';

/// Interceptor that attaches the JWT Bearer access token to outbound requests,
/// and attempts a transparent token refresh upon receiving a 401 Unauthorized.
class AuthInterceptor extends QueuedInterceptor {
  final TokenStorage _tokenStorage;
  final Dio _dio;

  AuthInterceptor(this._tokenStorage, this._dio);

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    // Skip token injection for public authentication and lookup endpoints
    final path = options.path;
    final isPublic = path.contains('/auth/login') ||
        path.contains('/auth/register') ||
        path.contains('/auth/refresh') ||
        path.contains('/auth/verify-otp');

    if (!isPublic) {
      final token = await _tokenStorage.getAccessToken();
      if (token != null && token.isNotEmpty) {
        options.headers['Authorization'] = 'Bearer $token';
      }
    }

    handler.next(options);
  }

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    // If we receive a 401 and we have a refresh token, attempt transparent refresh
    if (err.response?.statusCode == 401 && !err.requestOptions.path.contains('/auth/refresh')) {
      final refreshToken = await _tokenStorage.getRefreshToken();
      if (refreshToken != null && refreshToken.isNotEmpty) {
        try {
          // Create isolated dio client to avoid interceptor recursion
          final refreshDio = Dio(
            BaseOptions(
              baseUrl: AppConfig.apiBaseUrl,
              connectTimeout: const Duration(milliseconds: 10000),
              receiveTimeout: const Duration(milliseconds: 10000),
            ),
          );

          final refreshResponse = await refreshDio.post(
            '/auth/refresh',
            data: {'refreshToken': refreshToken},
          );

          if (refreshResponse.statusCode == 200 && refreshResponse.data != null) {
            final data = refreshResponse.data['data'] ?? refreshResponse.data;
            final newAccessToken = data['accessToken'] as String?;
            final newRefreshToken = data['refreshToken'] as String? ?? refreshToken;

            if (newAccessToken != null) {
              await _tokenStorage.saveTokens(
                accessToken: newAccessToken,
                refreshToken: newRefreshToken,
              );

              // Retry the original request with new token
              final opts = err.requestOptions;
              opts.headers['Authorization'] = 'Bearer $newAccessToken';

              final cloneReq = await _dio.request(
                opts.path,
                options: Options(
                  method: opts.method,
                  headers: opts.headers,
                  responseType: opts.responseType,
                  contentType: opts.contentType,
                ),
                data: opts.data,
                queryParameters: opts.queryParameters,
              );

              return handler.resolve(cloneReq);
            }
          }
        } catch (_) {
          // If refresh fails, clear tokens so user will be redirected to login
          await _tokenStorage.clearAll();
        }
      }
    }

    handler.next(err);
  }
}
