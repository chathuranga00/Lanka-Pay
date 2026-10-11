// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'Lanka Pay';

  @override
  String get simulatedBannerText => 'SIMULATED - test money only';

  @override
  String get welcomeTitle => 'Next-Gen Instant Payments for Sri Lanka';

  @override
  String get welcomeSubtitle =>
      'Seamless, secure, and instant fund transfers across Sri Lankan banks powered by LankaPay-ID.';

  @override
  String get getStarted => 'Get Started';

  @override
  String get signIn => 'Sign In';

  @override
  String get register => 'Register';

  @override
  String get phoneLabel => 'Mobile Number';

  @override
  String get phoneHint => 'e.g. 0771234567';

  @override
  String get passwordLabel => 'Password';

  @override
  String get navHome => 'Home';

  @override
  String get navAccounts => 'Accounts';

  @override
  String get navSend => 'Send';

  @override
  String get navQr => 'QR Pay';

  @override
  String get navHistory => 'History';

  @override
  String get navNotifications => 'Alerts';

  @override
  String get navSettings => 'Settings';
}
