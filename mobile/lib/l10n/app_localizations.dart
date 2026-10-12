import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_si.dart';
import 'app_localizations_ta.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations? of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations);
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('si'),
    Locale('ta'),
  ];

  /// The title of the application
  ///
  /// In en, this message translates to:
  /// **'Lanka Pay'**
  String get appTitle;

  /// Persistent banner warning that money is simulated
  ///
  /// In en, this message translates to:
  /// **'SIMULATED - test money only'**
  String get simulatedBannerText;

  /// No description provided for @welcomeTitle.
  ///
  /// In en, this message translates to:
  /// **'Next-Gen Instant Payments for Sri Lanka'**
  String get welcomeTitle;

  /// No description provided for @welcomeSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Seamless, secure, and instant fund transfers across Sri Lankan banks powered by LankaPay-ID.'**
  String get welcomeSubtitle;

  /// No description provided for @getStarted.
  ///
  /// In en, this message translates to:
  /// **'Get Started'**
  String get getStarted;

  /// No description provided for @signIn.
  ///
  /// In en, this message translates to:
  /// **'Sign In'**
  String get signIn;

  /// No description provided for @register.
  ///
  /// In en, this message translates to:
  /// **'Register'**
  String get register;

  /// No description provided for @phoneLabel.
  ///
  /// In en, this message translates to:
  /// **'Mobile Number'**
  String get phoneLabel;

  /// No description provided for @phoneHint.
  ///
  /// In en, this message translates to:
  /// **'e.g. 0771234567 or +94771234567'**
  String get phoneHint;

  /// No description provided for @passwordLabel.
  ///
  /// In en, this message translates to:
  /// **'Password'**
  String get passwordLabel;

  /// No description provided for @pinLabel.
  ///
  /// In en, this message translates to:
  /// **'6-Digit Security PIN'**
  String get pinLabel;

  /// No description provided for @fullNameLabel.
  ///
  /// In en, this message translates to:
  /// **'Full Name'**
  String get fullNameLabel;

  /// No description provided for @fullNameHint.
  ///
  /// In en, this message translates to:
  /// **'e.g. Kamal Perera'**
  String get fullNameHint;

  /// No description provided for @nicLabel.
  ///
  /// In en, this message translates to:
  /// **'National Identity Card (NIC)'**
  String get nicLabel;

  /// No description provided for @nicHint.
  ///
  /// In en, this message translates to:
  /// **'e.g. 199012345678 or 901234567V'**
  String get nicHint;

  /// No description provided for @roleLabel.
  ///
  /// In en, this message translates to:
  /// **'Account Role'**
  String get roleLabel;

  /// No description provided for @customerRole.
  ///
  /// In en, this message translates to:
  /// **'Customer / Personal'**
  String get customerRole;

  /// No description provided for @merchantRole.
  ///
  /// In en, this message translates to:
  /// **'Merchant / Business'**
  String get merchantRole;

  /// No description provided for @otpTitle.
  ///
  /// In en, this message translates to:
  /// **'OTP Verification'**
  String get otpTitle;

  /// No description provided for @otpSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Enter the 6-digit verification code sent to your mobile phone.'**
  String get otpSubtitle;

  /// No description provided for @verifyButton.
  ///
  /// In en, this message translates to:
  /// **'Verify & Continue'**
  String get verifyButton;

  /// No description provided for @resendOtp.
  ///
  /// In en, this message translates to:
  /// **'Resend Code'**
  String get resendOtp;

  /// No description provided for @forgotPin.
  ///
  /// In en, this message translates to:
  /// **'Forgot PIN / Account Recovery?'**
  String get forgotPin;

  /// No description provided for @recoverPinTitle.
  ///
  /// In en, this message translates to:
  /// **'Security Recovery'**
  String get recoverPinTitle;

  /// No description provided for @recoverPinSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Recover access by verifying your registered phone number and National Identity Card (NIC).'**
  String get recoverPinSubtitle;

  /// No description provided for @recoverSubmit.
  ///
  /// In en, this message translates to:
  /// **'Send Recovery Code'**
  String get recoverSubmit;

  /// No description provided for @termsAgree.
  ///
  /// In en, this message translates to:
  /// **'I agree to the Privacy Notice and Terms of Service'**
  String get termsAgree;

  /// No description provided for @privacyNoticeTitle.
  ///
  /// In en, this message translates to:
  /// **'Privacy Notice & Terms'**
  String get privacyNoticeTitle;

  /// No description provided for @privacyNoticeSubtitle.
  ///
  /// In en, this message translates to:
  /// **'How we protect your financial data under Sri Lanka Personal Data Protection Act (PDPA).'**
  String get privacyNoticeSubtitle;

  /// No description provided for @consentContinue.
  ///
  /// In en, this message translates to:
  /// **'Agree & Continue'**
  String get consentContinue;

  /// No description provided for @consentDecline.
  ///
  /// In en, this message translates to:
  /// **'Decline'**
  String get consentDecline;

  /// No description provided for @signOut.
  ///
  /// In en, this message translates to:
  /// **'Sign Out'**
  String get signOut;

  /// No description provided for @retryButton.
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get retryButton;

  /// No description provided for @navHome.
  ///
  /// In en, this message translates to:
  /// **'Home'**
  String get navHome;

  /// No description provided for @navAccounts.
  ///
  /// In en, this message translates to:
  /// **'Accounts'**
  String get navAccounts;

  /// No description provided for @navSend.
  ///
  /// In en, this message translates to:
  /// **'Send'**
  String get navSend;

  /// No description provided for @navQr.
  ///
  /// In en, this message translates to:
  /// **'QR Pay'**
  String get navQr;

  /// No description provided for @navHistory.
  ///
  /// In en, this message translates to:
  /// **'History'**
  String get navHistory;

  /// No description provided for @navNotifications.
  ///
  /// In en, this message translates to:
  /// **'Alerts'**
  String get navNotifications;

  /// No description provided for @navSettings.
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get navSettings;

  /// No description provided for @navMore.
  ///
  /// In en, this message translates to:
  /// **'More'**
  String get navMore;

  /// No description provided for @paymentQr.
  ///
  /// In en, this message translates to:
  /// **'Payment QR'**
  String get paymentQr;

  /// No description provided for @paymentRequests.
  ///
  /// In en, this message translates to:
  /// **'Payment Requests'**
  String get paymentRequests;

  /// No description provided for @transactionHistory.
  ///
  /// In en, this message translates to:
  /// **'Transaction History'**
  String get transactionHistory;

  /// No description provided for @privacyAndPaymentId.
  ///
  /// In en, this message translates to:
  /// **'Privacy & Payment-ID'**
  String get privacyAndPaymentId;

  /// No description provided for @securityAndDevices.
  ///
  /// In en, this message translates to:
  /// **'Security & Devices'**
  String get securityAndDevices;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'si', 'ta'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'si':
      return AppLocalizationsSi();
    case 'ta':
      return AppLocalizationsTa();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
