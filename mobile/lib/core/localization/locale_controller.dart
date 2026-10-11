import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

final localeProvider = NotifierProvider<LocaleController, Locale>(LocaleController.new);

class LocaleController extends Notifier<Locale> {
  static const String prefKey = 'app_locale';

  @override
  Locale build() {
    _loadLocale();
    return const Locale('en');
  }

  Future<void> _loadLocale() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final savedCode = prefs.getString(prefKey);
      if (savedCode != null && ['en', 'si', 'ta'].contains(savedCode)) {
        state = Locale(savedCode);
      }
    } catch (_) {
      state = const Locale('en');
    }
  }

  Future<void> setLocale(Locale locale) async {
    state = locale;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(prefKey, locale.languageCode);
    } catch (_) {}
  }

  Future<void> setLanguageCode(String code) async {
    await setLocale(Locale(code));
  }
}
