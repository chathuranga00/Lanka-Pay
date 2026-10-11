import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/core/widgets/simulated_banner.dart';
import 'package:lankapay_id/features/auth/presentation/consent_screen.dart';
import 'package:lankapay_id/features/auth/presentation/login_screen.dart';
import 'package:lankapay_id/features/auth/presentation/otp_verification_screen.dart';
import 'package:lankapay_id/features/auth/presentation/recovery_screen.dart';
import 'package:lankapay_id/features/auth/presentation/register_screen.dart';
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
      expect(continueBtnBefore.onPressed, isNull); // Disabled initially

      // Check terms checkbox
      await tester.scrollUntilVisible(find.byKey(const Key('consent_terms_checkbox')), 100);
      await tester.tap(find.byKey(const Key('consent_terms_checkbox')));
      await tester.pumpAndSettle();

      final ElevatedButton continueBtnPartial = tester.widget(continueBtnFinder);
      expect(continueBtnPartial.onPressed, isNull); // Still disabled

      // Check privacy checkbox
      await tester.scrollUntilVisible(find.byKey(const Key('consent_privacy_checkbox')), 100);
      await tester.tap(find.byKey(const Key('consent_privacy_checkbox')));
      await tester.pumpAndSettle();

      final ElevatedButton continueBtnReady = tester.widget(continueBtnFinder);
      expect(continueBtnReady.onPressed, isNotNull); // Enabled when both checked
    });
  });

  group('RegisterScreen Form Validation Tests', () {
    testWidgets('validates required fields, NIC format, and matching PINs', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createTestableWidget(const RegisterScreen()));
      await tester.pumpAndSettle();

      // Tap submit on empty form
      await tester.tap(find.byKey(const Key('register_submit_btn')));
      await tester.pumpAndSettle();

      expect(find.text('Full name must be at least 2 characters'), findsOneWidget);
      expect(find.text('Mobile number is required'), findsOneWidget);
      expect(find.text('NIC is required'), findsOneWidget);
      expect(find.text('PIN must be exactly 6 numeric digits'), findsOneWidget);

      // Enter invalid NIC and mismatching PINs
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

  group('LoginScreen Form & Validation Tests', () {
    testWidgets('validates phone and 6-digit PIN requirements', (WidgetTester tester) async {
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() => tester.view.resetPhysicalSize());

      await tester.pumpWidget(createTestableWidget(const LoginScreen()));
      await tester.pumpAndSettle();

      // Attempt submit without data
      await tester.tap(find.byKey(const Key('login_submit_btn')));
      await tester.pumpAndSettle();

      expect(find.text('Mobile number is required'), findsOneWidget);
      expect(find.text('PIN must be exactly 6 numeric digits'), findsOneWidget);

      // Enter valid format and submit
      await tester.enterText(find.byKey(const Key('login_phone_field')), '0771234567');
      await tester.enterText(find.byKey(const Key('login_pin_field')), '123456');
      await tester.tap(find.byKey(const Key('login_submit_btn')));
      await tester.pumpAndSettle();

      expect(find.text('Mobile number is required'), findsNothing);
      expect(find.text('PIN must be exactly 6 numeric digits'), findsNothing);
    });
  });

  group('OtpVerificationScreen Widget Tests', () {
    testWidgets('renders OTP entry and resend button', (WidgetTester tester) async {
      await tester.pumpWidget(
        createTestableWidget(
          const OtpVerificationScreen(
            userId: 101,
            phone: '+94771234567',
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('OTP Verification'), findsWidgets);
      expect(find.text('Enter the 6-digit verification code sent to +94771234567.'), findsOneWidget);
      expect(find.byKey(const Key('otp_input_field')), findsOneWidget);
      expect(find.byKey(const Key('otp_verify_btn')), findsOneWidget);
      expect(find.byKey(const Key('otp_resend_btn')), findsOneWidget);
    });
  });

  group('RecoveryScreen Widget Tests', () {
    testWidgets('renders recovery form with phone and NIC fields', (WidgetTester tester) async {
      await tester.pumpWidget(createTestableWidget(const RecoveryScreen()));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('recover_phone_field')), findsOneWidget);
      expect(find.byKey(const Key('recover_nic_field')), findsOneWidget);
      expect(find.byKey(const Key('recover_submit_btn')), findsOneWidget);

      await tester.tap(find.byKey(const Key('recover_submit_btn')));
      await tester.pumpAndSettle();

      expect(find.text('Mobile number is required'), findsOneWidget);
      expect(find.text('NIC is required'), findsOneWidget);
    });
  });
}
