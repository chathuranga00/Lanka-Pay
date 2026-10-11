import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lankapay_id/core/theme/app_theme.dart';
import 'package:lankapay_id/core/widgets/simulated_banner.dart';
import 'package:lankapay_id/features/auth/presentation/welcome_screen.dart';
import 'package:lankapay_id/features/home/presentation/home_screen.dart';
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

  group('WelcomeScreen Widget Tests', () {
    testWidgets('renders onboarding elements and action buttons', (WidgetTester tester) async {
      await tester.pumpWidget(createTestableWidget(const WelcomeScreen()));
      await tester.pumpAndSettle();

      // Check simulated warning is visible on onboarding
      expect(find.text('SIMULATED - TEST MONEY ONLY'), findsOneWidget);

      // Check action buttons exist
      expect(find.byKey(const Key('get_started_btn')), findsOneWidget);
      expect(find.byKey(const Key('sign_in_btn')), findsOneWidget);
      expect(find.text('Instant Fund Transfers'), findsOneWidget);
    });
  });

  group('HomeScreen Widget Tests', () {
    testWidgets('renders primary account card and quick action navigation tiles', (WidgetTester tester) async {
      await tester.pumpWidget(createTestableWidget(const HomeScreen()));
      await tester.pumpAndSettle();

      // Verify Account Card
      expect(find.text('Primary Account'), findsOneWidget);
      expect(find.text('LKR 125,450.00'), findsOneWidget);
      expect(find.text('SIMULATED'), findsOneWidget);

      // Verify Quick Action Labels
      expect(find.text('Quick Actions'), findsOneWidget);
      expect(find.text('Send'), findsOneWidget);
      expect(find.text('QR Pay'), findsOneWidget);
      expect(find.text('Accounts'), findsOneWidget);
      expect(find.text('History'), findsOneWidget);
    });
  });
}
