import 'package:flutter/services.dart';

/// Service to toggle screen recording and screenshot protection.
/// Uses FLAG_SECURE on Android and equivalent platform channel on iOS.
class ScreenSecurityService {
  ScreenSecurityService._();

  static const _channel = MethodChannel('com.lankapay.lankapay_id/security');

  /// Enables screenshot and screen recording protection (e.g. for card details reveal).
  static Future<void> enableSecure() async {
    try {
      await _channel.invokeMethod('enableSecure');
    } catch (_) {
      // Ignored if platform doesn't support or in test environment
    }
  }

  /// Disables screenshot and screen recording protection when leaving secure screens.
  static Future<void> disableSecure() async {
    try {
      await _channel.invokeMethod('disableSecure');
    } catch (_) {
      // Ignored if platform doesn't support or in test environment
    }
  }
}
