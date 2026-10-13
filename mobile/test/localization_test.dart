import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';
import 'package:lankapay_id/l10n/app_localizations_en.dart';
import 'package:lankapay_id/l10n/app_localizations_si.dart';
import 'package:lankapay_id/l10n/app_localizations_ta.dart';

void main() {
  group('Trilingual Localization Tests (en, si, ta)', () {
    test('1. Supported locales contains en, si, and ta', () {
      final supportedCodes =
          AppLocalizations.supportedLocales.map((l) => l.languageCode).toSet();
      expect(supportedCodes, containsAll(['en', 'si', 'ta']));
      expect(AppLocalizations.delegate.isSupported(const Locale('en')), isTrue);
      expect(AppLocalizations.delegate.isSupported(const Locale('si')), isTrue);
      expect(AppLocalizations.delegate.isSupported(const Locale('ta')), isTrue);
      expect(AppLocalizations.delegate.isSupported(const Locale('fr')), isFalse);
    });

    test('2. Lookup returns correct localization instance for each language', () {
      final en = lookupAppLocalizations(const Locale('en'));
      final si = lookupAppLocalizations(const Locale('si'));
      final ta = lookupAppLocalizations(const Locale('ta'));

      expect(en, isA<AppLocalizationsEn>());
      expect(si, isA<AppLocalizationsSi>());
      expect(ta, isA<AppLocalizationsTa>());
    });

    test('3. Key strings are non-empty and translated across en, si, ta', () {
      final en = lookupAppLocalizations(const Locale('en'));
      final si = lookupAppLocalizations(const Locale('si'));
      final ta = lookupAppLocalizations(const Locale('ta'));

      // Check simulated warning banner
      expect(en.simulatedBannerText, isNotEmpty);
      expect(si.simulatedBannerText, isNotEmpty);
      expect(ta.simulatedBannerText, isNotEmpty);
      expect(si.simulatedBannerText, isNot(equals(en.simulatedBannerText)));
      expect(ta.simulatedBannerText, isNot(equals(en.simulatedBannerText)));

      // Check welcome title
      expect(en.welcomeTitle, isNotEmpty);
      expect(si.welcomeTitle, isNotEmpty);
      expect(ta.welcomeTitle, isNotEmpty);

      // Check common auth actions
      expect(en.signIn, equals('Sign In'));
      expect(si.signIn, isNotEmpty);
      expect(ta.signIn, isNotEmpty);

      expect(en.register, equals('Register'));
      expect(si.register, isNotEmpty);
      expect(ta.register, isNotEmpty);

      // Check role labels
      expect(en.customerRole, contains('Customer'));
      expect(si.customerRole, isNotEmpty);
      expect(ta.customerRole, isNotEmpty);

      expect(en.merchantRole, contains('Merchant'));
      expect(si.merchantRole, isNotEmpty);
      expect(ta.merchantRole, isNotEmpty);

      // Check consent
      expect(en.consentContinue, isNotEmpty);
      expect(si.consentContinue, isNotEmpty);
      expect(ta.consentContinue, isNotEmpty);
    });

    testWidgets('4. Widget tree renders Sinhala locale correctly', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          locale: const Locale('si'),
          supportedLocales: AppLocalizations.supportedLocales,
          localizationsDelegates: const [
            AppLocalizations.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          home: Builder(
            builder: (context) {
              final l10n = AppLocalizations.of(context)!;
              return Scaffold(
                body: Column(
                  children: [
                    Text(l10n.simulatedBannerText),
                    Text(l10n.signIn),
                    Text(l10n.register),
                  ],
                ),
              );
            },
          ),
        ),
      );
      await tester.pumpAndSettle();

      final si = lookupAppLocalizations(const Locale('si'));
      expect(find.text(si.simulatedBannerText), findsOneWidget);
      expect(find.text(si.signIn), findsOneWidget);
      expect(find.text(si.register), findsOneWidget);
    });

    testWidgets('5. Widget tree renders Tamil locale correctly', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          locale: const Locale('ta'),
          supportedLocales: AppLocalizations.supportedLocales,
          localizationsDelegates: const [
            AppLocalizations.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          home: Builder(
            builder: (context) {
              final l10n = AppLocalizations.of(context)!;
              return Scaffold(
                body: Column(
                  children: [
                    Text(l10n.simulatedBannerText),
                    Text(l10n.signIn),
                    Text(l10n.register),
                  ],
                ),
              );
            },
          ),
        ),
      );
      await tester.pumpAndSettle();

      final ta = lookupAppLocalizations(const Locale('ta'));
      expect(find.text(ta.simulatedBannerText), findsOneWidget);
      expect(find.text(ta.signIn), findsOneWidget);
      expect(find.text(ta.register), findsOneWidget);
    });
  });
}
