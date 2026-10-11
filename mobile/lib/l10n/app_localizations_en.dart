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
  String get phoneHint => 'e.g. 0771234567 or +94771234567';

  @override
  String get passwordLabel => 'Password';

  @override
  String get pinLabel => '6-Digit Security PIN';

  @override
  String get fullNameLabel => 'Full Name';

  @override
  String get fullNameHint => 'e.g. Kamal Perera';

  @override
  String get nicLabel => 'National Identity Card (NIC)';

  @override
  String get nicHint => 'e.g. 199012345678 or 901234567V';

  @override
  String get roleLabel => 'Account Role';

  @override
  String get customerRole => 'Customer / Personal';

  @override
  String get merchantRole => 'Merchant / Business';

  @override
  String get otpTitle => 'OTP Verification';

  @override
  String get otpSubtitle =>
      'Enter the 6-digit verification code sent to your mobile phone.';

  @override
  String get verifyButton => 'Verify & Continue';

  @override
  String get resendOtp => 'Resend Code';

  @override
  String get forgotPin => 'Forgot PIN / Account Recovery?';

  @override
  String get recoverPinTitle => 'Security Recovery';

  @override
  String get recoverPinSubtitle =>
      'Recover access by verifying your registered phone number and National Identity Card (NIC).';

  @override
  String get recoverSubmit => 'Send Recovery Code';

  @override
  String get termsAgree => 'I agree to the Privacy Notice and Terms of Service';

  @override
  String get privacyNoticeTitle => 'Privacy Notice & Terms';

  @override
  String get privacyNoticeSubtitle =>
      'How we protect your financial data under Sri Lanka Personal Data Protection Act (PDPA).';

  @override
  String get consentContinue => 'Agree & Continue';

  @override
  String get consentDecline => 'Decline';

  @override
  String get signOut => 'Sign Out';

  @override
  String get retryButton => 'Retry';

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
