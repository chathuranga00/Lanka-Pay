import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/core/widgets/simulated_banner.dart';
import 'package:lankapay_id/features/accounts/presentation/add_bank_account_screen.dart';
import 'package:lankapay_id/features/auth/presentation/consent_screen.dart';
import 'package:lankapay_id/features/auth/presentation/register_screen.dart';
import 'package:lankapay_id/features/home/presentation/home_screen.dart';
import 'package:lankapay_id/features/settings/presentation/privacy_settings_screen.dart';
import 'package:lankapay_id/features/settings/presentation/security_settings_screen.dart';
import 'package:lankapay_id/l10n/app_localizations.dart';

Widget createTestableWidget(Widget child) {
  return ProviderScope(
    child: MaterialApp(
      theme: AppTheme.lightTheme,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      home: child,
    ),
  );
}

void main() {
  group('SimulatedBanner Widget Tests', () {
    testWidgets('renders simulated warning label correctly', (WidgetTester tester) async {
      await tester.pumpWidget(
        createTestableWidget(
          const Scaffold(
            body: SimulatedBanner(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('SIMULATED - TEST MONEY ONLY'), findsOneWidget);
      expect(find.byIcon(Icons.warning_amber_rounded), findsOneWidget);
    });
  });

  group('ConsentScreen Widget Tests', () {
    testWidgets('requires both terms and privacy consent before continuing', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createTestableWidget(const ConsentScreen()));
      await tester.pumpAndSettle();

      final continueBtnFinder = find.byKey(const Key('consent_continue_btn'));
      final ElevatedButton continueBtnBefore = tester.widget(continueBtnFinder);
      expect(continueBtnBefore.onPressed, isNull);

      await tester.scrollUntilVisible(find.byKey(const Key('consent_terms_checkbox')), 100);
      await tester.tap(find.byKey(const Key('consent_terms_checkbox')));
      await tester.pumpAndSettle();

      final ElevatedButton continueBtnPartial = tester.widget(continueBtnFinder);
      expect(continueBtnPartial.onPressed, isNull);

      await tester.scrollUntilVisible(find.byKey(const Key('consent_privacy_checkbox')), 100);
      await tester.tap(find.byKey(const Key('consent_privacy_checkbox')));
      await tester.pumpAndSettle();

      final ElevatedButton continueBtnReady = tester.widget(continueBtnFinder);
      expect(continueBtnReady.onPressed, isNotNull);
    });
  });

  group('RegisterScreen Form Validation Tests', () {
    testWidgets('validates required fields, NIC format, and matching PINs', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createTestableWidget(const RegisterScreen()));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('register_submit_btn')));
      await tester.pumpAndSettle();

      expect(find.text('Full name must be at least 2 characters'), findsOneWidget);
      expect(find.text('Mobile number is required'), findsOneWidget);
      expect(find.text('NIC is required'), findsOneWidget);
      expect(find.text('PIN must be exactly 6 numeric digits'), findsOneWidget);

      await tester.enterText(find.byKey(const Key('register_name_field')), 'Kamal Perera');
      await tester.enterText(find.byKey(const Key('register_phone_field')), '0771234567');
      await tester.enterText(find.byKey(const Key('register_nic_field')), 'INVALID_NIC');
      await tester.enterText(find.byKey(const Key('register_pin_field')), '123456');
      await tester.enterText(find.byKey(const Key('register_confirm_pin_field')), '654321');

      await tester.tap(find.byKey(const Key('register_submit_btn')));
      await tester.pumpAndSettle();

      expect(find.text('Enter a valid NIC (9 digits + V/X, or 12 digits)'), findsOneWidget);
      expect(find.text('PINs do not match'), findsOneWidget);
    });
  });

  group('HomeScreen Dashboard Tests', () {
    testWidgets('renders quick action buttons: Send, Scan QR, and Request', (WidgetTester tester) async {
      await tester.pumpWidget(createTestableWidget(const HomeScreen()));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.byKey(const Key('home_quick_send_btn')), findsOneWidget);
      expect(find.byKey(const Key('home_quick_scan_btn')), findsOneWidget);
      expect(find.byKey(const Key('home_quick_request_btn')), findsOneWidget);
      expect(find.text('Recent Activity'), findsOneWidget);
    });
  });

  group('AddBankAccountScreen Form Tests', () {
    testWidgets('validates account number and account holder name', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createTestableWidget(const AddBankAccountScreen()));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('add_bank_institution_dropdown')), findsOneWidget);
      expect(find.byKey(const Key('add_bank_number_field')), findsOneWidget);
      expect(find.byKey(const Key('add_bank_name_field')), findsOneWidget);
      expect(find.byKey(const Key('add_bank_submit_btn')), findsOneWidget);

      await tester.tap(find.byKey(const Key('add_bank_submit_btn')));
      await tester.pumpAndSettle();

      expect(find.text('Enter a valid account number'), findsOneWidget);
      expect(find.text('Account holder name is required'), findsOneWidget);
    });
  });

  group('PrivacySettingsScreen Widget Tests', () {
    testWidgets('renders phone discovery switch and emergency freeze switch', (WidgetTester tester) async {
      await tester.pumpWidget(createTestableWidget(const PrivacySettingsScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Payment Identity Registry'), findsOneWidget);
      expect(find.byKey(const Key('privacy_discoverable_switch')), findsOneWidget);
      expect(find.text('Freeze Payment Identity'), findsOneWidget);
      expect(find.byKey(const Key('privacy_freeze_switch')), findsOneWidget);
    });
  });

  group('SecuritySettingsScreen Widget Tests', () {
    testWidgets('renders biometrics, step-up PIN toggles and trusted devices list', (WidgetTester tester) async {
      await tester.pumpWidget(createTestableWidget(const SecuritySettingsScreen()));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('security_biometrics_switch')), findsOneWidget);
      expect(find.byKey(const Key('security_step_up_switch')), findsOneWidget);
      expect(find.text('Trusted Device Sessions'), findsOneWidget);
      expect(find.text('Pixel 8 Pro (Simulated)'), findsOneWidget);
      expect(find.text('THIS DEVICE'), findsOneWidget);
    });
  });
}
