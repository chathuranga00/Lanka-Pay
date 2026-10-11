import 'package:flutter/foundation.dart';

/// Compile-time and runtime environment configuration for the LankaPay mobile app.
/// Values are injected via `--dart-define` flags.
/// Example: `flutter run --dart-define=API_BASE_URL=http://localhost:8080/api/v1`
class AppConfig {
  const AppConfig._();

  /// API base URL injected via `--dart-define=API_BASE_URL=...`
  /// Defaults to localhost for Web/Desktop (`http://localhost:8080/api/v1`),
  /// or Android emulator host alias (`http://10.0.2.2:8080/api/v1`).
  static String get apiBaseUrl {
    const fromEnv = String.fromEnvironment('API_BASE_URL');
    if (fromEnv.isNotEmpty) return fromEnv;
    return kIsWeb ? 'http://localhost:8080/api/v1' : 'http://10.0.2.2:8080/api/v1';
  }

  /// Application environment identifier (e.g. dev, staging, prod)
  static const String environment = String.fromEnvironment(
    'APP_ENV',
    defaultValue: 'dev',
  );

  /// Connection timeout in milliseconds
  static const int connectTimeoutMs = int.fromEnvironment(
    'CONNECT_TIMEOUT_MS',
    defaultValue: 15000,
  );

  /// Receive timeout in milliseconds
  static const int receiveTimeoutMs = int.fromEnvironment(
    'RECEIVE_TIMEOUT_MS',
    defaultValue: 15000,
  );

  /// Banner text to ensure users and examiners know this is simulated
  static const String simulatedNoticeText = 'SIMULATED - test money only';
}
