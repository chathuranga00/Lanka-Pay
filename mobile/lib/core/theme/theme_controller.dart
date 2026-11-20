import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Manages application theme mode with persistence via [SharedPreferences].
class ThemeController extends Notifier<ThemeMode> {
  static const String prefKey = 'lankapay_theme_mode';

  @override
  ThemeMode build() {
    _loadPersistedTheme();
    return ThemeMode.system;
  }

  Future<void> _loadPersistedTheme() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final saved = prefs.getString(prefKey);
      if (saved != null) {
        state = switch (saved) {
          'light' => ThemeMode.light,
          'dark' => ThemeMode.dark,
          _ => ThemeMode.system,
        };
      }
    } catch (_) {
      // Default to ThemeMode.system on any storage failure
      state = ThemeMode.system;
    }
  }

  /// Sets theme mode and persists to SharedPreferences.
  Future<void> setThemeMode(ThemeMode mode) async {
    state = mode;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(prefKey, mode.name);
    } catch (_) {
      // Non-fatal persistence error
    }
  }

  /// Toggles between light and dark modes.
  Future<void> toggleTheme() async {
    final next = state == ThemeMode.dark ? ThemeMode.light : ThemeMode.dark;
    await setThemeMode(next);
  }
}

final themeControllerProvider = NotifierProvider<ThemeController, ThemeMode>(() {
  return ThemeController();
});
